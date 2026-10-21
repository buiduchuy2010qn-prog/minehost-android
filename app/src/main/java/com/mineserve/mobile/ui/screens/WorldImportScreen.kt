package com.mineserve.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mineserve.mobile.server.ExternalBackupStore
import com.mineserve.mobile.server.Worlds
import com.mineserve.mobile.ui.McViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private class LocalOnlyOpenDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
    }
}

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
    var pickerServer by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var confirmWorld by remember { mutableStateOf<String?>(null) }

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

    HostPage(
        "Nhập thế giới (Map) & Mod đi kèm",
        "Hỗ trợ nhập cả Map thường lẫn Map có sẵn Mod (.jar / mods.zip). Giữ nguyên world cũ và tự động cấu hình."
    ) {
        item { TextButton(onClick = onBack) { Text("← Quay lại") } }

        item {
            HostCard("📖 Hướng dẫn chạy Map có sẵn Mod & Tránh lỗi Cello error 8") {
                Text("• Bước 1 (Chọn đúng Core): Map có Mod bắt buộc phải chạy trên Core Forge, Fabric hoặc NeoForge có cùng phiên bản Minecraft với Map (cài tại mục Tải xuống).", style = MaterialTheme.typography.bodySmall)
                Text("• Bước 2 (Chọn tệp ZIP từ Bộ nhớ máy / Download): Khuyên dùng danh sách 'Chọn nhanh tệp ZIP có sẵn trong máy (Download)' ở mục số 2 bên dưới để đọc trực tiếp từ bộ nhớ điện thoại — KHÔNG chọn tệp trên Google Drive đám mây (tránh lỗi tbd: Cello error 8).", style = MaterialTheme.typography.bodySmall)
                Text("• Bước 3 (Tự động cài Mod đi kèm): Nếu trong file .zip của Map đã có sẵn thư mục mods/ và config/, MineHost sẽ TỰ ĐỘNG cài toàn bộ Mod vào máy chủ! Nếu Mod để rời, hãy dùng mục số 3 bên dưới.", style = MaterialTheme.typography.bodySmall)
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
            HostCard("2 · Chọn tệp Map (.zip — Hỗ trợ cả gói Map kèm sẵn mods/)") {
                Text("• Định dạng hỗ trợ: Tệp .zip chứa level.dat ở gốc, trong thư mục world/ hoặc lồng trong thư mục giải nén (kèm world_nether/, world_the_end/, mods/, config/).")
                Text("• Cách tránh lỗi 'tbd: Cello error 8': Lỗi Cello error 8 xảy ra khi bạn chọn tệp .zip đang nằm trên Google Drive đám mây chưa tải về máy. Hãy chọn tệp trực tiếp từ danh sách thư mục Download bên dưới!", style = MaterialTheme.typography.bodySmall)

                if (!hasStoragePerm) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "🔓 Cấp quyền đọc thư mục Download (Khuyên dùng)",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Cấp quyền 'Truy cập tất cả tệp' để ứng dụng quét và liệt kê sẵn các tệp Map (.zip) trong thư mục Download của bạn — bấm 1 chạm là nhập ngay, không bị lỗi Google Drive Cello error 8!",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📱 Tệp ZIP tìm thấy trong máy (Download / Bộ nhớ):",
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = {
                            hasStoragePerm = ExternalBackupStore.hasPermission(context)
                            vm.refreshLocalWorldArchives()
                        }) {
                            Text("🔄 Làm mới")
                        }
                    }
                    if (localArchives.isEmpty()) {
                        Text(
                            "Chưa tìm thấy tệp .zip hoặc .mcworld nào trong thư mục Download. Hãy tải tệp ZIP của Map về thư mục Download rồi bấm '🔄 Làm mới', hoặc dùng nút chọn tệp bên dưới.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            localArchives.take(10).forEach { item ->
                                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(item.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${item.folderLabel} • ${Worlds.formatSize(item.sizeBytes)}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                pickerServer = config.activeCoreName
                                                name = ""
                                                pendingLocalName = item.name
                                                pendingLocalPath = item.path
                                            },
                                            enabled = ready && selected != null && !running && !busy && !modBusy
                                        ) {
                                            Text("Nhập ngay")
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
                Text("Quản lý danh sách các thế giới (Map)")
            }
        }
        item {
            Text(
                "💡 Mẹo xử lý lỗi sau khi bật máy chủ với Map Mod: Nếu máy chủ báo lỗi thiếu Mod hoặc xung đột Mod (Client-only mod như OptiFine, Sodium, Iris, Oculus...), hãy vào mục Quản lý -> Mod & Plugin để tắt các Mod chỉ dành cho Client.",
                style = MaterialTheme.typography.bodySmall
            )
        }
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
                        label = { Text("Tên world (để trống sẽ lấy theo tên tệp ZIP)") },
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
