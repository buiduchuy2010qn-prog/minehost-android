package com.mineserve.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mineserve.mobile.server.Worlds
import com.mineserve.mobile.ui.McViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun WorldsScreen(vm: McViewModel, onBack: () -> Unit, onImport: () -> Unit = {}) {
    val worlds by vm.worlds.collectAsState()
    val loading by vm.worldsLoading.collectAsState()
    val active by vm.activeWorldName.collectAsState()
    val ready by vm.isBootstrapped.collectAsState()
    val importing by vm.worldImportBusy.collectAsState()
    val config by vm.config.collectAsState()
    val runningFlow = remember(vm) { vm.serverState.map { it.isRunning }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = true)
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var exportServer by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableStateOf<Worlds.WorldInfo?>(null) }
    var activate by remember { mutableStateOf<Worlds.WorldInfo?>(null) }
    LaunchedEffect(ready, config.activeCoreName) { if (ready) vm.loadWorlds() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && exportServer == config.activeCoreName) pendingExport?.let { vm.exportWorldToUri(it, uri) }
        pendingExport = null
    }
    HostPage("Quản lý thế giới", "Đang chọn: " + active.ifBlank { "world" }) {
        item { TextButton(onClick = onBack) { Text("← Quay lại") } }
        item { Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Nhập thế giới có sẵn") } }
        item { Column {
            if (running) Text("Dừng server trước khi nhập, xuất, đổi hoặc đặt lại world.")
            if (!ready) Text("Cài môi trường chạy ở Tổng quan trước.")
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (worlds.isEmpty()) Text("Chưa có world. Bạn có thể nhập ZIP trước khi bật server lần đầu.")
            TextButton(onClick = vm::loadWorlds, enabled = ready && !loading && !importing) { Text("Làm mới danh sách") }
        } }
        items(worlds, key = { it.name }) { world ->
            HostCard(world.name) {
                Text((if (world.kind == Worlds.Kind.Java) "Java" else "Bedrock") + " • " +
                    world.sizeText + (if (world.sizeTruncated) "+" else "") + (if (world.isActive) " • Đang chọn" else ""))
                if (!world.isActive) Button(onClick = { activate = world }, enabled = !running && !importing) { Text("Dùng world này") }
                OutlinedButton(onClick = {
                    pendingExport = world.name
                    exportServer = config.activeCoreName
                    exporter.launch("World_" + world.name + ".zip")
                }, enabled = !running && !importing) { Text("Xuất ZIP để sao lưu") }
                TextButton(onClick = { reset = world }, enabled = !running && !importing) { Text("Sao lưu rồi đặt lại") }
            }
        }
        item { Text("Lưu ZIP vào Download hoặc nơi ngoài dữ liệu app; kiểm tra bản sao trước khi gỡ ứng dụng.", style = MaterialTheme.typography.bodySmall) }
    }
    activate?.let { world ->
        AlertDialog(onDismissRequest = { activate = null }, title = { Text("Dùng " + world.name + "?") },
            text = { Text("World cũ vẫn còn. Lần bật server tiếp theo sẽ dùng world này.") },
            confirmButton = { TextButton(onClick = { activate = null; vm.setActiveWorld(world.name) },
                enabled = !running && !importing) { Text("Chọn") } },
            dismissButton = { TextButton(onClick = { activate = null }) { Text("Hủy") } })
    }
    reset?.let { world ->
        AlertDialog(onDismissRequest = { reset = null }, title = { Text("Đặt lại " + world.name + "?") },
            text = { Text("Tạo bản sao lưu trước rồi xóa world này và các chiều đi kèm. Server sẽ tạo world mới lần bật sau. Nếu sao lưu thất bại, không xóa world.") },
            confirmButton = { TextButton(onClick = { reset = null; vm.resetWorld(world.name) },
                enabled = !running && !importing) { Text("Sao lưu và đặt lại") } },
            dismissButton = { TextButton(onClick = { reset = null }) { Text("Hủy") } })
    }
}
