package com.mineserve.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.mineserve.mobile.server.Worlds
import com.mineserve.mobile.ui.McViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun WorldImportScreen(vm: McViewModel, onBack: () -> Unit, onWorlds: () -> Unit) {
    val config by vm.config.collectAsState()
    val ready by vm.isBootstrapped.collectAsState()
    val busy by vm.worldImportBusy.collectAsState()
    val result by vm.worldImportStatus.collectAsState()
    val runningFlow = remember(vm) { vm.serverState.map { it.isRunning }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = true)
    val selected = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerServer by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var confirmWorld by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingUri = uri?.toString() }
    HostPage("Nhập thế giới có sẵn", "Thêm một bản sao vào server. Giữ nguyên world cũ và ZIP gốc.") {
        item { TextButton(onClick = onBack) { Text("← Quay lại") } }
        item { HostCard("1 · Server đích") {
            Text(selected?.let { it.name + " • " + it.core.displayName + " " + it.version } ?: "Chưa chọn server")
            if (!ready) Text("Cài môi trường chạy ở Tổng quan trước.")
            if (running) Text("Dừng server trước khi nhập hoặc đổi world.")
            Text("Chọn cùng phiên bản Minecraft và bộ mod của world để tránh mất dữ liệu.")
        } }
        item { HostCard("2 · Tệp world") {
            Text("ZIP có level.dat ở gốc hoặc trong thư mục world/. Có thể kèm world_nether/ và world_the_end/.")
            Text("Giới hạn: ZIP 2 GB, giải nén 4 GB, 100.000 tệp; cần thêm 256 MB trống. World Bedrock (.mcworld/db) không tự chuyển sang Java.")
            Button(onClick = {
                pickerServer = config.activeCoreName
                picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }, enabled = ready && selected != null && !running && !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Chọn ZIP trên điện thoại")
            }
        } }
        if (busy || result.serverName == config.activeCoreName) item { HostCard("3 · Kết quả") {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Server: " + (result.serverName ?: ""))
                Text(result.phase)
                if (result.copiedBytes > 0) Text("Đã đọc " + Worlds.formatSize(result.copiedBytes))
                Text("Giữ ứng dụng mở đến khi hoàn tất.")
            } else if (result.error != null) {
                Text(result.error ?: "", color = MaterialTheme.colorScheme.error)
            } else if (result.world != null) {
                Text(result.phase)
                Text("Tên world: " + result.world)
                Button(onClick = { confirmWorld = result.world }, enabled = !running) { Text("Dùng world này") }
            } else Text("Chưa nhập tệp nào.")
        } }
        item { OutlinedButton(onClick = onWorlds, modifier = Modifier.fillMaxWidth()) { Text("Quản lý các world") } }
        item { Text("Paper và Vanilla/Fabric có thể bố trí Nether/End khác nhau. Dùng bản sao và kiểm tra các chiều sau lần bật đầu.", style = MaterialTheme.typography.bodySmall) }
    }
    pendingUri?.let { value ->
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text("Nhập vào " + (pickerServer ?: "server") + "?") },
            text = { Column {
                Text("World trùng tên sẽ thêm số. Chưa tự kích hoạt world vừa nhập.")
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) },
                    label = { Text("Tên world (trống: theo tên ZIP)") }, singleLine = true)
            } },
            confirmButton = { TextButton(onClick = {
                pendingUri = null
                vm.importWorldFromUri(Uri.parse(value), name, pickerServer)
            }, enabled = !busy && !running && selected != null && pickerServer == config.activeCoreName) { Text("Bắt đầu nhập") } },
            dismissButton = { TextButton(onClick = { pendingUri = null }) { Text("Hủy") } }
        )
    }
    confirmWorld?.let { world ->
        AlertDialog(onDismissRequest = { confirmWorld = null },
            title = { Text("Dùng " + world + "?") },
            text = { Text("Server sẽ mở world này ở lần bật tiếp theo. World cũ vẫn còn trong danh sách.") },
            confirmButton = { TextButton(onClick = {
                confirmWorld = null
                if (result.serverName == config.activeCoreName) vm.setActiveWorld(world)
            }, enabled = !running && !busy && result.serverName == config.activeCoreName) { Text("Chọn") } },
            dismissButton = { TextButton(onClick = { confirmWorld = null }) { Text("Hủy") } })
    }
}
