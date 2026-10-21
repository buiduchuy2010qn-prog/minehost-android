package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
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
import com.mineserve.mobile.ui.PlayConnectionMode
import com.mineserve.mobile.ui.SubPage

@Composable
internal fun HostPage(title: String, subtitle: String, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "heading") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MINEHOST / ANDROID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
internal fun HostCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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

private data class OverviewState(
    val isRunning: Boolean = false, val startupPhase: StartupPhase = StartupPhase.Idle,
    val tps: Double = 0.0, val onlinePlayers: Int = 0, val usedMemoryMb: Long = 0
)

@Composable
fun MineHostOverview(vm: McViewModel, navigate: (SubPage) -> Unit, onConsole: () -> Unit) {
    val config by vm.config.collectAsState()
    val overview = remember(vm) {
        vm.serverState.map { OverviewState(it.isRunning, it.startupPhase, it.tps, it.onlinePlayers, it.usedMemoryMb) }.distinctUntilChanged()
    }
    val state by overview.collectAsState(initial = OverviewState())
    val device by vm.deviceProfile.collectAsState()
    val applying by vm.applyingDeviceTuning.collectAsState()
    val importing by vm.worldImportBusy.collectAsState()
    val bootstrapRunning by vm.bootstrapRunning.collectAsState()
    val ready by vm.isBootstrapped.collectAsState()
    val bootstrapError by vm.bootstrapError.collectAsState()
    val stopping by vm.stopInFlight.collectAsState()
    val downloading by vm.isDownloadingCore.collectAsState()
    val installing by vm.isInstalling.collectAsState()
    val addonBusy by vm.pluginDownloadProgress.collectAsState()
    val javaOperation by vm.javaOperation.collectAsState()
    val lanIp by vm.lanIp.collectAsState()
    val lanInterfaceLabel by vm.lanInterfaceLabel.collectAsState()
    val lanLastUpdated by vm.lanLastUpdatedText.collectAsState()
    val vpnActiveWarning by vm.vpnActiveWarning.collectAsState()
    val connectionCheckStatus by vm.connectionCheckStatus.collectAsState()
    val connectionCheckRunning by vm.connectionCheckRunning.collectAsState()
    val playConnectionMode by vm.playConnectionMode.collectAsState()
    val playitState by vm.playitState.collectAsState()
    val port by vm.serverPort.collectAsState()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val active = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    var confirm by remember { mutableStateOf<String?>(null) }
    val heapLimit = device?.maxManualHeapMb ?: 2048
    var heap by remember(config.maxHeapMb, heapLimit) { mutableFloatStateOf(config.maxHeapMb.toFloat().coerceIn(512f, heapLimit.toFloat())) }
    var confirmTuning by remember { mutableStateOf(false) }
    val busy = downloading || installing || addonBusy.isNotEmpty() || javaOperation != null || importing || applying
    val status = when {
        stopping -> "Đang lưu và dừng…"
        state.startupPhase == StartupPhase.Failed -> "Khởi động thất bại"
        state.isRunning && state.startupPhase != StartupPhase.Ready -> "Đang khởi động…"
        state.isRunning -> "Đang hoạt động"
        else -> "Đã dừng"
    }
    LaunchedEffect(Unit) { vm.refreshLanIp() }
    HostPage("Server của bạn", "Điện thoại là máy chủ. Không cần thuê VPS để chơi cùng mạng Wi-Fi.") {
        item {
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
        }
        item {
            if (!ready) HostCard("Chuẩn bị lần đầu") {
                if (bootstrapRunning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Đang cài môi trường chạy vào điện thoại. Giữ ứng dụng mở và kết nối Wi-Fi; bước này có thể mất vài phút.")
                } else {
                    Text(bootstrapError ?: "Sẵn sàng cài môi trường chạy. Dùng Wi-Fi và giữ ứng dụng mở.")
                    Button(onClick = vm::retryBootstrap) { Text(if (bootstrapError == null) "Cài môi trường chạy" else "Thử lại") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("TPS", if (state.isRunning && state.tps > 0) "%.1f".format(state.tps) else "—", Modifier.weight(1f))
                Metric("Người chơi", if (state.isRunning && state.startupPhase == StartupPhase.Ready) state.onlinePlayers.toString() else "—", Modifier.weight(1f))
                Metric("RAM / MB", if (state.isRunning && state.usedMemoryMb > 0) state.usedMemoryMb.toString() else "—", Modifier.weight(1f))
            }
        }
        item {
            Text("“—” là chưa có số đo, không phải 0. TPS có thể không được core này hỗ trợ.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            if (active == null) {
                HostAction("1. Tải core server", "Paper: plugin • Fabric: mod. Chọn đúng phiên bản Minecraft.", Icons.Outlined.Download) { navigate(SubPage.Cores) }
                HostAction("2. Cài Java tương thích", "Paper 1.20–1.21.11: Java 21 • 26.1+: Java 25. Core khác: xem yêu cầu của core.", Icons.Outlined.Memory) { navigate(SubPage.EnvManager) }
            } else {
                HostCard("Mời bạn bè & Địa chỉ kết nối (Dọn sẵn)") {
                    val localhostAddress = "127.0.0.1:$port"
                    val lanAddress = lanIp.takeIf {
                        it.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) && it != "0.0.0.0" && !it.startsWith("127.")
                    }?.let { "$it:$port" }
                    val playitAddress = playitState.publicAddress?.takeIf { it.isNotBlank() }

                    Text(
                        "Chọn chế độ bạn muốn chơi để MineHost dọn sẵn đúng 1 địa chỉ cần dùng:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        val isLanMode = playConnectionMode == PlayConnectionMode.SameNetwork
                        if (isLanMode) {
                            Button(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.SameNetwork) },
                                modifier = Modifier.weight(1f)
                            ) { Text("🏠 Cùng mạng Wi-Fi") }
                            OutlinedButton(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.Everywhere) },
                                modifier = Modifier.weight(1f)
                            ) { Text("🌍 Kết nối mọi nơi") }
                        } else {
                            OutlinedButton(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.SameNetwork) },
                                modifier = Modifier.weight(1f)
                            ) { Text("🏠 Cùng mạng Wi-Fi") }
                            Button(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.Everywhere) },
                                modifier = Modifier.weight(1f)
                            ) { Text("🌍 Kết nối mọi nơi") }
                        }
                    }

                    // Hộp địa chỉ dọn sẵn theo chế độ đang chọn
                    val recommendedTitle = if (playConnectionMode == PlayConnectionMode.SameNetwork) {
                        "👉 ĐỊA CHỈ DÙNG CHO BẠN BÈ CÙNG WI-FI / HOTSPOT:"
                    } else {
                        "👉 ĐỊA CHỈ DÙNG CHO BẠN BÈ Ở MỌI NƠI (4G / KHÁC WI-FI):"
                    }
                    val recommendedAddr = if (playConnectionMode == PlayConnectionMode.SameNetwork) lanAddress else playitAddress
                    val recommendedStatusNote = if (playConnectionMode == PlayConnectionMode.SameNetwork) {
                        if (lanAddress != null) {
                            "Giao diện: $lanInterfaceLabel • Cập nhật lúc: $lanLastUpdated" +
                                if (!state.isRunning) "\n⚠️ Đã có địa chỉ LAN nhưng Server đang dừng — Hãy bấm Bật server trước khi vào." else ""
                        } else {
                            "Chưa xác định được địa chỉ mạng LAN. Hãy bật Wi-Fi hoặc Điểm phát sóng di động rồi bấm Làm mới."
                        }
                    } else {
                        when {
                            playitState.isRunning && playitAddress != null ->
                                "Trạng thái Playit.gg: Đã kết nối (${playitState.lastUpdatedText.ifBlank { "vừa xong" }}). Bạn bè dùng 4G hoặc Wi-Fi khác nhập đúng địa chỉ có cả số cổng này!"
                            playitState.isRunning && !playitState.claimUrl.isNullOrBlank() && !playitState.isClaimed ->
                                "Trạng thái Playit.gg: Cần xác nhận liên kết (Claim) lần đầu. Hãy bấm nút Mở trang xác nhận bên dưới."
                            playitState.isRunning ->
                                "Trạng thái Playit.gg: Đang kết nối và lấy địa chỉ công khai có cổng từ máy chủ Playit.gg..."
                            else ->
                                "Trạng thái Playit.gg: Đang tắt. Hãy bấm nút 'Bật Playit.gg ngay' bên dưới để lấy địa chỉ công khai."
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                recommendedTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                recommendedAddr ?: if (playConnectionMode == PlayConnectionMode.SameNetwork) "Chưa xác định được địa chỉ mạng LAN" else "Chưa có địa chỉ Playit.gg (Hãy bật Playit.gg)",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                recommendedStatusNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (vpnActiveWarning && playConnectionMode == PlayConnectionMode.SameNetwork) {
                                Text(
                                    "⚠️ Phát hiện VPN đang bật trên máy: có thể làm bạn bè cùng Wi-Fi không nhìn thấy server.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { recommendedAddr?.let { clipboard.setText(AnnotatedString(it)) } },
                                    enabled = recommendedAddr != null,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Sao chép địa chỉ") }
                                OutlinedButton(
                                    onClick = {
                                        recommendedAddr?.let { addr ->
                                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    android.content.Intent.EXTRA_TEXT,
                                                    "Vào server Minecraft của mình nhé!\nĐịa chỉ Server: $addr\nPhiên bản: ${active.core.displayName} ${active.version}"
                                                )
                                            }
                                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Chia sẻ địa chỉ Server"))
                                        }
                                    },
                                    enabled = recommendedAddr != null,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Gửi bạn bè") }
                            }
                            if (playConnectionMode == PlayConnectionMode.Everywhere) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    if (!playitState.isRunning) {
                                        Button(
                                            onClick = { vm.startPlayitTunnel() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("🚀 Bật kết nối mọi nơi (Playit.gg)") }
                                    } else {
                                        OutlinedButton(
                                            onClick = { vm.stopPlayitTunnel() },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Tắt Playit.gg") }
                                        if (playitState.claimUrl.isNotBlank() && !playitState.isClaimed) {
                                            Button(
                                                onClick = { uriHandler.openUri(playitState.claimUrl) },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("🔑 Xác nhận Claim") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bảng phân biệt rõ 3 loại địa chỉ (Mục 16.1)
                    HorizontalDivider()
                    Text(
                        "Bảng phân biệt 3 loại địa chỉ kết nối:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "1️⃣ Trên chính điện thoại này (Localhost): $localhostAddress\n   → Chỉ dùng khi bạn mở game Minecraft ngay trên chính chiếc điện thoại này.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "2️⃣ Trong cùng mạng Wi-Fi / Hotspot (LAN): ${lanAddress ?: "Chưa xác định (Wi-Fi/Hotspot tắt)"}\n   → Dùng cho bạn bè bắt chung Wi-Fi hoặc điểm phát sóng ($lanInterfaceLabel).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "3️⃣ Kết nối từ Internet mọi nơi (Playit.gg): ${playitAddress ?: if (playitState.isRunning) "Đang lấy địa chỉ..." else "Chưa bật Playit.gg"}\n   → Dùng cho bạn bè ở xa dùng 4G hoặc mạng Wi-Fi khác (không lộ IP mạng nhà; địa chỉ có thể đổi nếu tạo lại tunnel).",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(localhostAddress))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Chép 127.0.0.1:$port") }
                        OutlinedButton(
                            onClick = { vm.refreshLanIp(notifyOnChange = true) },
                            modifier = Modifier.weight(1f)
                        ) { Text("🔄 Làm mới IP") }
                    }
                    OutlinedButton(
                        onClick = { vm.checkConnectionReachability() },
                        enabled = !connectionCheckRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (connectionCheckRunning) "⏳ Đang kiểm tra kết nối..." else "🩺 Kiểm tra kết nối thực tế (Localhost / LAN / Playit)")
                    }
                    connectionCheckStatus?.let { checkReport ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                checkReport,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    TextButton(onClick = { navigate(SubPage.Network) }) {
                        Text("Mở trang Quản lý Mạng & Playit.gg chi tiết →")
                    }
                }
            }
        }
        item {
            HostAction(
                "Người chơi & Lịch sử tham gia",
                "Xem ai đang trực tuyến, lịch sử từng người chơi đã vào server, cấp OP, Whitelist, Ban.",
                Icons.Outlined.People
            ) { navigate(SubPage.PlayerList) }
        }
        item {
            if (active?.core?.supportsMods == true) {
                HostAction(
                    "Quản lý Mod & Giải mã Thư mục Full Mod",
                    "Giải mã thư mục/ZIP full mod, chuyển mod sang server mới hoặc tải tự động từ Modrinth.",
                    Icons.Outlined.Extension
                ) { navigate(SubPage.Plugins) }
            } else if (active != null) {
                HostAction(
                    "Quản lý Plugin",
                    "Cài đặt và bật/tắt plugin cho server.",
                    Icons.Outlined.Extension
                ) { navigate(SubPage.Plugins) }
            }
        }
        item {
            HostAction("Nhập thế giới có sẵn", "ZIP world Java", Icons.Outlined.FileUpload) { navigate(SubPage.WorldImport) }
        }
        item {
            HostCard("Tối ưu theo điện thoại") {
                Text(device?.let { "RAM hệ thống: %.1f GB • %d luồng CPU".format(it.totalMb / 1024.0, it.processors) } ?: "Đang đọc cấu hình máy…")
                Text(device?.let { "Gợi ý RAM server: " + it.recommendedHeapMb + " MB" } ?: "")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tự chọn RAM khi bật server", Modifier.weight(1f))
                    Switch(checked = config.autoHeap, onCheckedChange = vm::setAutoHeap, enabled = !state.isRunning && !busy)
                }
                if (!config.autoHeap) {
                    Text("Đã lưu: " + config.maxHeapMb + " MB")
                    Slider(value = heap, onValueChange = { heap = it },
                        valueRange = 512f..heapLimit.toFloat(), steps = (heapLimit - 512) / 256 - 1,
                        enabled = !state.isRunning && !busy,
                        onValueChangeFinished = { vm.setMaxHeap((heap.toInt() / 256) * 256) })
                }
                device?.let { profile ->
                    Text("Gợi ý: view " + profile.viewDistance + " • simulation " + profile.simulationDistance +
                        " • tối đa " + profile.maxPlayers + " người.")
                    OutlinedButton(onClick = { confirmTuning = true }, enabled = active != null && ready && !state.isRunning && !busy) {
                        Text(if (applying) "Đang áp dụng…" else "Áp dụng cấu hình nhẹ")
                    }
                }
                TextButton(onClick = vm::refreshDeviceProfile) { Text("Đọc lại bộ nhớ") }
                Text("RAM tự động chừa bộ nhớ cho Android và Java. Độ mượt còn phụ thuộc CPU, nhiệt độ, chunk và plugin/mod.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            HostAction("Console trực tiếp", "Xem log thật và gửi lệnh Minecraft.", Icons.Outlined.Terminal, onConsole)
        }
        item {
            HostAction("Danh sách server", "Chọn hoặc nhập server có sẵn. Một server chạy tại một thời điểm.", Icons.Outlined.Dns) { navigate(SubPage.ServerList) }
        }
        item {
            HostAction("Sao lưu thế giới", "Tạo và khôi phục bản sao trước khi đổi mod/plugin.", Icons.Outlined.Backup) { navigate(SubPage.Backup) }
        }
        item {
            Text("Giữ máy thoáng, theo dõi nhiệt độ. Android có thể dừng ứng dụng nền; không cam kết host 24/7.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmTuning) AlertDialog(
        onDismissRequest = { confirmTuning = false },
        title = { Text("Áp dụng cho " + (active?.name ?: "server") + "?") },
        text = { Text("Bật RAM tự động và thay view-distance, simulation-distance, max-players theo gợi ý ở trên. Lưu bản sao server.properties trước khi sửa.") },
        confirmButton = { TextButton(onClick = { confirmTuning = false; vm.applyDeviceTuning() }) { Text("Áp dụng") } },
        dismissButton = { TextButton(onClick = { confirmTuning = false }) { Text("Hủy") } }
    )
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
        item {
            HostAction("Core Minecraft", "Paper cho plugin; Fabric cho mod. Không trộn tùy tiện hai loại.", Icons.Outlined.Dns) { navigate(SubPage.Cores) }
        }
        item {
            HostAction("Plugin & mod", "Tìm trên Modrinth, nhập JAR hoặc quản lý mục đã cài.", Icons.Outlined.Extension) { navigate(SubPage.Plugins) }
        }
        item {
            HostAction("Java + Bedrock cùng chơi", "Hướng dẫn Geyser / Floodgate cho server Paper.", Icons.Outlined.Devices) { navigate(SubPage.Crossplay) }
        }
        item {
            HostAction("Môi trường Java", "Cài Java phù hợp với core và phiên bản game.", Icons.Outlined.Memory) { navigate(SubPage.EnvManager) }
        }
        item {
            HostCard("Cài mod như thế nào?") {
                Text("1. Tạo/chọn server Fabric, Forge, NeoForge hoặc Quilt.\n2. Dừng server, sao lưu world.\n3. Mở Plugin & mod, chọn đúng phiên bản + loader và cài JAR server.\n4. Cài thêm dependency được tác giả yêu cầu.\n5. Bật server, đọc log. Mod yêu cầu ở client phải được bạn bè cài cùng phiên bản.")
                Text("Dependency chưa được tự động giải quyết; luôn kiểm tra trang dự án. Nếu có dependency bắt buộc, bản thử nghiệm yêu cầu tải bộ JAR tương thích từ trang dự án và nhập thủ công.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            HostCard("OptiFine có không?") {
                Text("OptiFine, shader và mod chỉ dành cho client không cài vào server. Cài chúng ở máy chơi Minecraft Java. Paper dùng plugin; Fabric có thể dùng mod tối ưu phía server tương thích.")
            }
        }
        item {
            HostCard("Trước khi cài JAR") {
                Text("Plugin/mod là mã thực thi, có thể đọc hoặc thay đổi dữ liệu server. Chỉ cài từ nguồn tin cậy; kiểm tra JAR không thay thế quét mã độc. Luôn sao lưu.")
            }
        }
    }
}

@Composable
fun MineHostManage(navigate: (SubPage) -> Unit) {
    val uri = LocalUriHandler.current
    HostPage("Quản lý", "Mọi thứ của server, tập trung ở một nơi.") {
        item {
            HostAction("Server & cấu hình", "Danh sách server, RAM, Java và core đang chọn.", Icons.Outlined.Dns) { navigate(SubPage.ServerList) }
        }
        item {
            HostAction("Người chơi", "Whitelist, OP, kick và ban. OP không phải quyền quản trị app.", Icons.Outlined.People) { navigate(SubPage.PlayerList) }
        }
        item {
            HostAction("Luật chơi", "Difficulty, gamemode, PvP và server.properties.", Icons.Outlined.Tune) { navigate(SubPage.Properties) }
        }
        item {
            HostAction("Nhập thế giới có sẵn", "ZIP world Java", Icons.Outlined.FileUpload) { navigate(SubPage.WorldImport) }
        }
        item {
            HostAction("Thế giới", "Nhập, xuất, chọn và quản lý world.", Icons.Outlined.Public) { navigate(SubPage.WorldManage) }
        }
        item {
            HostAction("Sao lưu & khôi phục", "Bảo vệ world trước khi nâng cấp.", Icons.Outlined.Backup) { navigate(SubPage.Backup) }
        }
        item {
            HostAction("Kết nối bạn bè", "LAN, cổng và tunnel. Kiểm tra TCP/UDP theo nhu cầu.", Icons.Outlined.Lan) { navigate(SubPage.Network) }
        }
        item {
            HostAction("Lịch tự động", "Android có thể hạn chế tác vụ chạy nền.", Icons.Outlined.Schedule) { navigate(SubPage.Automation) }
        }
        item {
            HostAction("Chạy nền & pin", "Thông báo foreground, giữ Wi-Fi và tiết kiệm pin.", Icons.Outlined.BatteryChargingFull) { navigate(SubPage.KeepAlive) }
        }
        item {
            HostAction("Chẩn đoán", "Kiểm tra môi trường và lỗi khởi động.", Icons.Outlined.HealthAndSafety) { navigate(SubPage.Diagnostics) }
        }
        item {
            HostAction("Báo cáo crash", "Xem nguyên nhân và log lần chạy trước.", Icons.Outlined.BugReport) { navigate(SubPage.CrashReports) }
        }
        item {
            HostAction("Cài đặt nâng cao", "Một số màn hình kế thừa còn tiếng Anh/Trung.", Icons.Outlined.Settings) { navigate(SubPage.AppSettings) }
        }
        item {
            HostCard("MineHost • bản thử nghiệm") {
                Text("Dựa trên Minexa (GPL-3.0-or-later), tùy biến giao diện và quy tắc an toàn. Chưa xác minh chạy Minecraft trên điện thoại của bạn.")
                TextButton(onClick = { uri.openUri("https://github.com/duchilocket-oss/minehost-android") }) { Text("Mã nguồn & bản build trên GitHub") }
                Text("Repo riêng tư cần đăng nhập trong trình duyệt. Không nhúng tài khoản GitHub vào APK.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun MineHostCrossplay(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    HostPage("Java + Bedrock", "Dùng Paper + Geyser. Không phải mọi mod Java đều chơi được bằng Bedrock.") {
        item {
            TextButton(onClick = onBack) { Text("← Quay lại") }
        }
        item {
            HostCard("1. Chuẩn bị server Paper") {
                Text("Cài Paper và Java đúng yêu cầu, bật thử server, sau đó dừng và sao lưu. Chọn bản Geyser/Floodgate hỗ trợ phiên bản server; đừng mặc định bản mới nhất sẽ tương thích.")
            }
        }
        item {
            HostCard("2. Tải plugin chính thức") {
                Text("Tải Geyser-Spigot.jar và floodgate-spigot.jar chính thức. Vào Kho cài đặt → Plugin & mod → nhập JAR. Không dùng bản proxy/Fabric với Paper.")
                TextButton(onClick = { uri.openUri("https://geysermc.org/download") }) { Text("Mở trang tải Geyser / Floodgate") }
            }
        }
        item {
            HostCard("3. Cấu hình và kiểm tra") {
                Text("Bật một lần để tạo cấu hình, rồi dừng. Theo tài liệu hiện hành để đặt auth-type: floodgate ở đúng mục trong cấu hình Geyser. Giữ online-mode=true cho Java; không công khai key.pem của Floodgate.")
                Text("Bedrock thường dùng UDP 19132. Kiểm tra cả yêu cầu TCP/UDP và NetherNet theo tài liệu hiện hành. Tunnel chỉ có TCP có thể không đủ.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { uri.openUri("https://geysermc.org/wiki/geyser/setup/") }) { Text("Hướng dẫn Geyser chính thức") }
                TextButton(onClick = { uri.openUri("https://geysermc.org/wiki/floodgate/") }) { Text("Hướng dẫn Floodgate") }
            }
        }
        item {
            HostCard("4. Mời bạn bè") {
                Text("Java dùng địa chỉ Java của server. Bedrock dùng địa chỉ/cổng Bedrock trong cấu hình. Thử cùng Wi-Fi trước, rồi thiết lập truy cập Internet. Mod cần client Java riêng không được tự động chuyển đổi.")
            }
        }
    }
}

