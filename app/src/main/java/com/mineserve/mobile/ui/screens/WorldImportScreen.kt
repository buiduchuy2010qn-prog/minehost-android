package com.mineserve.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mineserve.mobile.server.Worlds
import com.mineserve.mobile.ui.McViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun WorldImportScreen(vm: McViewModel, onBack: () -> Unit, onWorlds: () -> Unit) {
    val config by vm.config.collectAsState()
    val ready by vm.isBootstrapped.collectAsState()
    val busy by vm.worldImportBusy.collectAsState()
    val result by vm.worldImportStatus.collectAsState()
    val modCount by vm.worldModCount.collectAsState()
    val modBusy by vm.worldModBusy.collectAsState()
    val modStatus by vm.worldModStatus.collectAsState()
    val runningFlow = remember(vm) { vm.serverState.map { it.isRunning }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = true)
    val selected = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerServer by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var confirmWorld by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(config.activeCoreName, ready) {
        if (ready) vm.refreshWorldModCount()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingUri = uri?.toString()
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
            HostCard("📖 Hướng dẫn chạy Map có sẵn Mod (3 bước chuẩn)") {
                Text("• Bước 1 (Chọn đúng Core): Map có Mod bắt buộc phải chạy trên Core Forge, Fabric hoặc NeoForge có cùng phiên bản Minecraft với Map (cài tại mục Tải xuống).", style = MaterialTheme.typography.bodySmall)
                Text("• Bước 2 (Nhập Map .zip): Bấm 'Chọn tệp Map (.zip)' bên dưới. Nếu trong file .zip của bạn đã gói sẵn cả thư mục world/ lẫn thư mục mods/ và config/, ứng dụng sẽ TỰ ĐỘNG tách và cài toàn bộ Mod vào máy chủ!", style = MaterialTheme.typography.bodySmall)
                Text("• Bước 3 (Cài Mod rời nếu có): Nếu tác giả Map để tệp Mod (.jar) hoặc gói mods.zip riêng, hãy dùng mục số 3 bên dưới để chép thẳng vào máy chủ.", style = MaterialTheme.typography.bodySmall)
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
                Text("• Định dạng hỗ trợ: Tệp .zip chứa level.dat ở gốc hoặc trong thư mục world/ (kèm world_nether/, world_the_end/).")
                Text("• Hỗ trợ Map Mod trọn gói: Nếu trong tệp .zip có sẵn thư mục mods/*.jar và config/, MineHost sẽ tự động cài đặt chúng vào máy chủ cho bạn!")
                Text("• Giới hạn an toàn: ZIP tối đa 2 GB, giải nén tối đa 4 GB (cần trống ít nhất 256 MB). Map Bedrock (.mcworld) chỉ dùng cho Core PowerNukkitX.", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        pickerServer = config.activeCoreName
                        picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    },
                    enabled = ready && selected != null && !running && !busy && !modBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📂 Chọn tệp Map (.zip) trên điện thoại")
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
                            "💡 Hướng dẫn khắc phục: 1) Đảm bảo tệp .zip là Map Java hợp lệ (có level.dat). 2) Nếu tệp là gói Modpack CurseForge (.mrpack/manifest.json không có thư mục world/), hãy nhập ở mục Tải xuống -> Nhập Modpack. 3) Kiểm tra dung lượng trống của máy.",
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
