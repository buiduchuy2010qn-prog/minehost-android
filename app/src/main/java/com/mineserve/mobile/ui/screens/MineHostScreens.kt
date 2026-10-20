package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mineserve.mobile.data.StartupPhase
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SubPage

@Composable
private fun HostPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("MINEHOST / ANDROID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HostCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun HostAction(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun MineHostOverview(vm: McViewModel, navigate: (SubPage) -> Unit, onConsole: () -> Unit) {
    val config by vm.config.collectAsState()
    val state by vm.serverState.collectAsState()
    val ready by vm.isBootstrapped.collectAsState()
    val bootstrapError by vm.bootstrapError.collectAsState()
    val stopping by vm.stopInFlight.collectAsState()
    val downloading by vm.isDownloadingCore.collectAsState()
    val installing by vm.isInstalling.collectAsState()
    val addonBusy by vm.pluginDownloadProgress.collectAsState()
    val javaOperation by vm.javaOperation.collectAsState()
    val lanIp by vm.lanIp.collectAsState()
    val port by vm.serverPort.collectAsState()
    val clipboard = LocalClipboardManager.current
    val active = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    var confirm by remember { mutableStateOf<String?>(null) }
    var heap by remember(config.maxHeapMb) { mutableFloatStateOf(config.maxHeapMb.toFloat()) }
    val busy = downloading || installing || addonBusy.isNotEmpty() || javaOperation != null
    val status = when {
        stopping -> "Đang lưu và dừng…"
        state.startupPhase == StartupPhase.Failed -> "Khởi động thất bại"
        state.isRunning && state.startupPhase != StartupPhase.Ready -> "Đang khởi động…"
        state.isRunning -> "Đang hoạt động"
        else -> "Đã dừng"
    }
    LaunchedEffect(Unit) { vm.refreshLanIp() }
    HostPage("Server của bạn", "Điện thoại là máy chủ. Không cần thuê VPS để chơi cùng mạng Wi-Fi.") {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(status.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(active?.name ?: "Chưa có server", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(active?.let { it.core.displayName + " · " + it.version } ?: "Tạo thế giới đầu tiên để mời bạn bè.")
                if (state.isRunning && state.startupPhase != StartupPhase.Ready) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        if (active == null) navigate(SubPage.Cores)
                        else if (state.isRunning) confirm = "stop"
                        else vm.startServer()
                    },
                    enabled = ready && !stopping && (state.isRunning || !busy),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(if (state.isRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (active == null) "Tạo server" else if (state.isRunning) "Dừng an toàn" else "Bật server")
                }
                if (state.isRunning) OutlinedButton(onClick = { confirm = "restart" }, enabled = !stopping && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Lưu và khởi động lại")
                }
            }
        }
        if (!ready) HostCard("Chuẩn bị lần đầu") {
            if (bootstrapError == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Đang cài môi trường chạy vào điện thoại. Giữ ứng dụng mở và kết nối Wi-Fi; bước này có thể mất vài phút.")
            } else {
                Text(bootstrapError ?: "")
                Button(onClick = vm::retryBootstrap) { Text("Thử lại") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("TPS", if (state.isRunning && state.tps > 0) "%.1f".format(state.tps) else "—", Modifier.weight(1f))
            Metric("Người chơi", if (state.isRunning) state.onlinePlayers.toString() else "—", Modifier.weight(1f))
            Metric("RAM / MB", if (state.isRunning && state.usedMemoryMb > 0) state.usedMemoryMb.toString() else "—", Modifier.weight(1f))
        }
        Text("“—” là chưa có số đo, không phải 0. TPS có thể không được core này hỗ trợ.", style = MaterialTheme.typography.bodySmall)
        if (active == null) {
            HostAction("1. Tải core server", "Paper: plugin • Fabric: mod. Chọn đúng phiên bản Minecraft.", Icons.Outlined.Download) { navigate(SubPage.Cores) }
            HostAction("2. Cài Java tương thích", "Paper 1.20–1.21.11: Java 21 • 26.1+: Java 25. Core khác: xem yêu cầu của core.", Icons.Outlined.Memory) { navigate(SubPage.EnvManager) }
        } else {
            HostCard("Mời bạn bè • cùng Wi-Fi") {
                val address = lanIp.takeIf { it.isNotBlank() && it != "0.0.0.0" }?.let { "$it:$port" }
                Text(address ?: "Chưa có địa chỉ LAN", style = MaterialTheme.typography.titleLarge)
                Text("Java: Multiplayer → Add Server. Địa chỉ này không phải địa chỉ Internet công cộng.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { address?.let { clipboard.setText(AnnotatedString(it)) } }, enabled = address != null) { Text("Sao chép địa chỉ") }
                TextButton(onClick = { navigate(SubPage.Network) }) { Text("Bạn ở mạng khác? Thiết lập kết nối →") }
            }
        }
        HostCard("RAM dành cho server") {
            Text(heap.toInt().toString() + " MB / máy 8 GB", style = MaterialTheme.typography.titleLarge)
            Slider(value = heap, onValueChange = { heap = it }, valueRange = 1024f..4096f, steps = 11,
                enabled = !state.isRunning, onValueChangeFinished = { vm.setMaxHeap(heap.toInt()) })
            Text("Bắt đầu với 2048 MB và 3–5 người. Còn phụ thuộc CPU, nhiệt độ, world và plugin; không dành hết 8 GB cho Java.", style = MaterialTheme.typography.bodySmall)
        }
        HostAction("Console trực tiếp", "Xem log thật và gửi lệnh Minecraft.", Icons.Outlined.Terminal, onConsole)
        HostAction("Danh sách server", "Chọn hoặc nhập server có sẵn. Một server chạy tại một thời điểm.", Icons.Outlined.Dns) { navigate(SubPage.ServerList) }
        HostAction("Sao lưu thế giới", "Tạo và khôi phục bản sao trước khi đổi mod/plugin.", Icons.Outlined.Backup) { navigate(SubPage.Backup) }
        Text("Giữ máy thoáng, theo dõi nhiệt độ. Android có thể dừng ứng dụng nền; không cam kết host 24/7.", style = MaterialTheme.typography.bodySmall)
    }
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == "restart") "Khởi động lại server?" else "Dừng server?") },
            text = { Text("Người chơi sẽ bị ngắt kết nối. MineHost gửi lệnh dừng và chờ server lưu dữ liệu trước.") },
            confirmButton = { TextButton(onClick = {
                confirm = null
                if (action == "restart") vm.restartServer() else vm.stopServer()
            }) { Text("Đồng ý") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Hủy") } }
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun MineHostLibrary(navigate: (SubPage) -> Unit) {
    HostPage("Kho cài đặt", "Chọn đúng core, phiên bản Minecraft và loại add-on.") {
        HostAction("Core Minecraft", "Paper cho plugin; Fabric cho mod. Không trộn tùy tiện hai loại.", Icons.Outlined.Dns) { navigate(SubPage.Cores) }
        HostAction("Plugin & mod", "Tìm trên Modrinth, nhập JAR hoặc quản lý mục đã cài.", Icons.Outlined.Extension) { navigate(SubPage.Plugins) }
        HostAction("Java + Bedrock cùng chơi", "Hướng dẫn Geyser / Floodgate cho server Paper.", Icons.Outlined.Devices) { navigate(SubPage.Crossplay) }
        HostAction("Môi trường Java", "Cài Java phù hợp với core và phiên bản game.", Icons.Outlined.Memory) { navigate(SubPage.EnvManager) }
        HostCard("Cài mod như thế nào?") {
            Text("1. Tạo/chọn server Fabric, Forge, NeoForge hoặc Quilt.\n2. Dừng server, sao lưu world.\n3. Mở Plugin & mod, chọn đúng phiên bản + loader và cài JAR server.\n4. Cài thêm dependency được tác giả yêu cầu.\n5. Bật server, đọc log. Mod yêu cầu ở client phải được bạn bè cài cùng phiên bản.")
            Text("Dependency chưa được tự động giải quyết; luôn kiểm tra trang dự án. Nếu có dependency bắt buộc, bản thử nghiệm yêu cầu tải bộ JAR tương thích từ trang dự án và nhập thủ công.", style = MaterialTheme.typography.bodySmall)
        }
        HostCard("OptiFine có không?") {
            Text("OptiFine, shader và mod chỉ dành cho client không cài vào server. Cài chúng ở máy chơi Minecraft Java. Paper dùng plugin; Fabric có thể dùng mod tối ưu phía server tương thích.")
        }
        HostCard("Trước khi cài JAR") {
            Text("Plugin/mod là mã thực thi, có thể đọc hoặc thay đổi dữ liệu server. Chỉ cài từ nguồn tin cậy; kiểm tra JAR không thay thế quét mã độc. Luôn sao lưu.")
        }
    }
}

@Composable
fun MineHostManage(navigate: (SubPage) -> Unit) {
    val uri = LocalUriHandler.current
    HostPage("Quản lý", "Mọi thứ của server, tập trung ở một nơi.") {
        HostAction("Server & cấu hình", "Danh sách server, RAM, Java và core đang chọn.", Icons.Outlined.Dns) { navigate(SubPage.ServerList) }
        HostAction("Người chơi", "Whitelist, OP, kick và ban. OP không phải quyền quản trị app.", Icons.Outlined.People) { navigate(SubPage.PlayerList) }
        HostAction("Luật chơi", "Difficulty, gamemode, PvP và server.properties.", Icons.Outlined.Tune) { navigate(SubPage.Properties) }
        HostAction("Thế giới", "Nhập, xuất, chọn và quản lý world.", Icons.Outlined.Public) { navigate(SubPage.WorldManage) }
        HostAction("Sao lưu & khôi phục", "Bảo vệ world trước khi nâng cấp.", Icons.Outlined.Backup) { navigate(SubPage.Backup) }
        HostAction("Kết nối bạn bè", "LAN, cổng và tunnel. Kiểm tra TCP/UDP theo nhu cầu.", Icons.Outlined.Lan) { navigate(SubPage.Network) }
        HostAction("Lịch tự động", "Android có thể hạn chế tác vụ chạy nền.", Icons.Outlined.Schedule) { navigate(SubPage.Automation) }
        HostAction("Chạy nền & pin", "Thông báo foreground, giữ Wi-Fi và tiết kiệm pin.", Icons.Outlined.BatteryChargingFull) { navigate(SubPage.KeepAlive) }
        HostAction("Chẩn đoán", "Kiểm tra môi trường và lỗi khởi động.", Icons.Outlined.HealthAndSafety) { navigate(SubPage.Diagnostics) }
        HostAction("Báo cáo crash", "Xem nguyên nhân và log lần chạy trước.", Icons.Outlined.BugReport) { navigate(SubPage.CrashReports) }
        HostAction("Cài đặt nâng cao", "Một số màn hình kế thừa còn tiếng Anh/Trung.", Icons.Outlined.Settings) { navigate(SubPage.AppSettings) }
        HostCard("MineHost • bản thử nghiệm") {
            Text("Dựa trên Minexa (GPL-3.0-or-later), tùy biến giao diện và quy tắc an toàn. Chưa xác minh chạy Minecraft trên điện thoại của bạn.")
            TextButton(onClick = { uri.openUri("https://github.com/duchilocket-oss/minehost-android") }) { Text("Mã nguồn & bản build trên GitHub") }
            Text("Repo riêng tư cần đăng nhập trong trình duyệt. Không nhúng tài khoản GitHub vào APK.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MineHostCrossplay(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    HostPage("Java + Bedrock", "Dùng Paper + Geyser. Không phải mọi mod Java đều chơi được bằng Bedrock.") {
        TextButton(onClick = onBack) { Text("← Quay lại") }
        HostCard("1. Chuẩn bị server Paper") {
            Text("Cài Paper và Java đúng yêu cầu, bật thử server, sau đó dừng và sao lưu. Chọn bản Geyser/Floodgate hỗ trợ phiên bản server; đừng mặc định bản mới nhất sẽ tương thích.")
        }
        HostCard("2. Tải plugin chính thức") {
            Text("Tải Geyser-Spigot.jar và floodgate-spigot.jar chính thức. Vào Kho cài đặt → Plugin & mod → nhập JAR. Không dùng bản proxy/Fabric với Paper.")
            TextButton(onClick = { uri.openUri("https://geysermc.org/download") }) { Text("Mở trang tải Geyser / Floodgate") }
        }
        HostCard("3. Cấu hình và kiểm tra") {
            Text("Bật một lần để tạo cấu hình, rồi dừng. Theo tài liệu hiện hành để đặt auth-type: floodgate ở đúng mục trong cấu hình Geyser. Giữ online-mode=true cho Java; không công khai key.pem của Floodgate.")
            Text("Bedrock thường dùng UDP 19132. Kiểm tra cả yêu cầu TCP/UDP và NetherNet theo tài liệu hiện hành. Tunnel chỉ có TCP có thể không đủ.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { uri.openUri("https://geysermc.org/wiki/geyser/setup/") }) { Text("Hướng dẫn Geyser chính thức") }
            TextButton(onClick = { uri.openUri("https://geysermc.org/wiki/floodgate/") }) { Text("Hướng dẫn Floodgate") }
        }
        HostCard("4. Mời bạn bè") {
            Text("Java dùng địa chỉ Java của server. Bedrock dùng địa chỉ/cổng Bedrock trong cấu hình. Thử cùng Wi-Fi trước, rồi thiết lập truy cập Internet. Mod cần client Java riêng không được tự động chuyển đổi.")
        }
    }
}
