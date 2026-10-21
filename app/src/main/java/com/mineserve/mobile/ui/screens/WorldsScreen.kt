package com.mineserve.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val worlds by vm.worlds.collectAsState()
    val loading by vm.worldsLoading.collectAsState()
    val active by vm.activeWorldName.collectAsState()
    val ready by vm.isBootstrapped.collectAsState()
    val importing by vm.worldImportBusy.collectAsState()
    val importStatus by vm.worldImportStatus.collectAsState()
    val healthReport by vm.worldHealthReport.collectAsState()
    val config by vm.config.collectAsState()
    val serverStorageMap by vm.serverStorageMap.collectAsState()
    val activeStorage = config.activeCoreName?.let { serverStorageMap[it] }
    val runningFlow = remember(vm) { vm.serverState.map { it.isRunning }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = true)

    var pendingExport by remember { mutableStateOf<String?>(null) }
    var exportServer by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableStateOf<Worlds.WorldInfo?>(null) }
    var activate by remember { mutableStateOf<Worlds.WorldInfo?>(null) }
    var renameTarget by remember { mutableStateOf<Worlds.WorldInfo?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var pendingFolderUri by remember { mutableStateOf<Uri?>(null) }
    var folderWorldName by remember { mutableStateOf("") }

    LaunchedEffect(ready, config.activeCoreName) {
        if (ready) {
            vm.loadWorlds()
            vm.refreshServerStorage()
        }
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

    HostPage(
        "Quản lý Thế giới (World)",
        "Thế giới đang chọn (level-name): " + active.ifBlank { "world" }
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                TextButton(
                    onClick = vm::loadWorlds,
                    enabled = ready && !loading && !importing
                ) {
                    Text("🔄 Làm mới danh sách")
                }
            }
        }

        item {
            HostCard("🌍 Công cụ Nhập & Quản lý Thế giới toàn diện") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MinecraftPixelArtIcon(kind = MinecraftArtKind.GRASS, size = 34.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Nhập từ ZIP hoặc Thư mục • Xuất ZIP • Đổi tên • Kiểm tra lỗi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Hỗ trợ tự động nhận diện thư mục cha chứa world con, gộp kèm chiều Nether/The End và cài tự động thư mục mods/ đi kèm.",
                            color = Muted,
                            fontSize = 11.sp
                        )
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
                        Text("📦 Nhập từ ZIP / Map Mod", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { folderImporter.launch(null) },
                        enabled = ready && !running && !importing,
                        modifier = Modifier.weight(1f),
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, Indigo)
                    ) {
                        Text("📂 Nhập từ Thư mục", color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (running) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "⚠️ Server đang chạy: Hãy dừng server trước khi nhập, xuất, đổi tên, chuyển đổi hoặc đặt lại thế giới để tránh hỏng dữ liệu.",
                        color = Coral,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!ready) {
                    Spacer(Modifier.height(6.dp))
                    Text("Hãy cài môi trường chạy ở trang Tổng quan trước.", color = Coral, fontSize = 11.sp)
                }
                if (importing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(importStatus.phase.ifBlank { "Đang xử lý thế giới…" }, fontSize = 11.sp, color = Indigo)
                }
                if (loading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (worlds.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Chưa có thư mục thế giới nào. Bạn có thể nhập từ tệp ZIP / Thư mục ngay bây giờ, hoặc khởi chạy server để tự sinh thế giới mới.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        items(worlds, key = { it.name }) { world ->
            val isJava = world.kind == Worlds.Kind.Java
            val isBedrockServer = vm.isPowerNukkitXActive()
            val compatible = (isJava && !isBedrockServer) || (!isJava && isBedrockServer)

            HostCard(world.name) {
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
                        color = if (compatible) Indigo else Coral
                    )
                    if (world.isActive) {
                        Surface(
                            color = Mint.copy(alpha = 0.18f),
                            shape = McShape.Chip,
                            border = BorderStroke(1.dp, Mint.copy(alpha = 0.55f))
                        ) {
                            Text(
                                "✅ Đang chọn (level-name)",
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

                // Hiển thị chi tiết dung lượng từng tệp & thư mục bên trong Map đang chọn
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
                                "📂 Dung lượng từng thư mục / tệp trong Map '${world.name}' (Tổng: ${vm.formatStorageBytes(activeStorage.worldBytes)}):",
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
                        Text("🔍 Kiểm tra thế giới", color = Indigo, fontSize = 11.sp)
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
                        Text("📤 Xuất ZIP sao lưu", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = { reset = world },
                        enabled = !running && !importing
                    ) {
                        Text("🔄 Sao lưu & Đặt lại", color = Coral, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Text(
                "💡 Mẹo an toàn: Luôn xuất ZIP thế giới ra thư mục Download trước khi gỡ ứng dụng hoặc nâng cấp phiên bản Minecraft lớn.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
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
