package com.mineserve.mobile.ui.screens

import androidx.compose.ui.res.stringResource
import com.mineserve.mobile.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.data.FrpLocalTarget
import com.mineserve.mobile.data.FrpProfile
import com.mineserve.mobile.data.FrpProfileKind
import com.mineserve.mobile.data.FrpProfiles
import com.mineserve.mobile.data.McConfig
import com.mineserve.mobile.data.TunnelState
import com.mineserve.mobile.data.TunnelStatus
import com.mineserve.mobile.server.tunnel.StunDiagnostics
import com.mineserve.mobile.ui.AddressCopyRow
import com.mineserve.mobile.ui.BackBar
import com.mineserve.mobile.ui.DebouncedTextField
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.HintNote
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.mineserve.mobile.ui.PlayConnectionMode
import com.mineserve.mobile.ui.PlayitStage
import com.mineserve.mobile.ui.PlayitTunnelController
import com.mineserve.mobile.ui.PlayitUiState
import com.mineserve.mobile.ui.StatLine
import com.mineserve.mobile.ui.StatusPill
import com.mineserve.mobile.ui.SwipeableInstantSnackbarHost
import com.mineserve.mobile.ui.VirtualLanEndpoint
import com.mineserve.mobile.ui.WarnNote
import com.mineserve.mobile.ui.showInstantSnackbar
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.FieldBg
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.IndigoSoft
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.MintSoft
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.TrackBg
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkScreen(
    vm: McViewModel,
    onBack: () -> Unit,
    showBackBar: Boolean = true,
    onOpenFrpEditor: (String?) -> Unit = {}
) {
    val config by vm.config.collectAsState()
    val serverPort by vm.serverPort.collectAsState()
    val lanIp by vm.lanIp.collectAsState()
    val serverState by vm.serverState.collectAsState()
    val frpState by vm.frpState.collectAsState()
    val stunState by vm.stunState.collectAsState()
    val stunDiag by vm.stunDiagnostics.collectAsState()
    val playitState by vm.playitState.collectAsState()
    val playMode by vm.playConnectionMode.collectAsState()
    val maskAddress by vm.maskAddressInUi.collectAsState()
    val consoleLines by vm.consoleLines.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var virtualLanEndpoints by remember { mutableStateOf(PlayitTunnelController.detectVirtualLanEndpoints()) }
    var showAdvancedTunnels by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshLanIp()
        virtualLanEndpoints = PlayitTunnelController.detectVirtualLanEndpoints()
    }
    LaunchedEffect(Unit) { vm.loadServerProperties() }
    LaunchedEffect(Unit) {
        vm.messageFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.errorFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SwipeableInstantSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            if (showBackBar) {
                BackBar(title = stringResource(R.string.s541), onBack = onBack)
            }
            HeaderBlock(
                eyebrow = stringResource(R.string.eyebrow_network),
                title = stringResource(R.string.s585),
                statusBarPadding = !showBackBar
            )

            // ── 0. Bộ chọn 2 Chế Độ Chơi & Khiên Bảo Mật Chống Lộ IP Mạng Nhà ──
            NetworkModeAndPrivacyCard(
                playMode = playMode,
                maskAddress = maskAddress,
                onSelectMode = { vm.setPlayConnectionMode(it) },
                onToggleMask = { vm.setMaskAddressInUi(!maskAddress) }
            )

            if (playMode == PlayConnectionMode.SameNetwork) {
                // ── CHẾ ĐỘ 1: CHƠI CÙNG MẠNG (Wi-Fi / Phát 4G Hotspot) ────────
                LocalAddressCard(
                    vm = vm,
                    serverPort = serverPort,
                    lanIp = lanIp,
                    serverRunning = serverState.isRunning,
                    maskAddress = maskAddress,
                    context = context
                )
            } else {
                // ── CHẾ ĐỘ 2: BẬT KẾT NỐI MỌI NƠI (Qua 4G / Khác Wi-Fi) ────────
                PlayitEverywhereCard(
                    vm = vm,
                    playitState = playitState,
                    serverRunning = serverState.isRunning,
                    serverPort = serverPort,
                    maskAddress = maskAddress,
                    context = context
                )

                VirtualLanCard(
                    serverPort = serverPort,
                    endpoints = virtualLanEndpoints,
                    maskAddress = maskAddress,
                    onRefresh = {
                        vm.refreshLanIp()
                        virtualLanEndpoints = PlayitTunnelController.detectVirtualLanEndpoints()
                    },
                    onCopy = { url -> vm.copyPublicUrl(context, url) },
                    context = context
                )

                // Địa chỉ cục bộ rút gọn để chủ phòng vẫn copy nhanh được 127.0.0.1
                LocalAddressCard(
                    vm = vm,
                    serverPort = serverPort,
                    lanIp = lanIp,
                    serverRunning = serverState.isRunning,
                    maskAddress = maskAddress,
                    context = context
                )

                // Nhật ký đường hầm
                TunnelLogPreview(consoleLines, frpState, stunState, playitState)

                // Thu gọn các đường hầm kỹ thuật phức tạp (FRP / STUN) vào mục nâng cao để giao diện gọn đẹp
                McCard(title = "⚙️ Đường hầm nâng cao (Tự cấu hình FRP / STUN P2P)") {
                    Text(
                        "Dành cho người dùng nâng cao có máy chủ FRP riêng hoặc muốn dùng kết nối P2P STUN truyền thống. Nếu dùng Playit.gg ở trên thì KHÔNG cần bật mục này.",
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showAdvancedTunnels = !showAdvancedTunnels },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (showAdvancedTunnels) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = Indigo,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (showAdvancedTunnels) "Thu gọn cấu hình FRP & STUN nâng cao"
                            else "Mở cấu hình FRP & STUN nâng cao",
                            color = Indigo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (showAdvancedTunnels) {
                    FrpCard(
                        vm = vm,
                        config = config,
                        frpState = frpState,
                        serverRunning = serverState.isRunning,
                        serverPort = serverPort,
                        onStart = { vm.startFrp() },
                        onStop = { vm.stopFrp() },
                        onOpenEditor = onOpenFrpEditor,
                        onCopy = { url -> vm.copyPublicUrl(context, url) }
                    )

                    StunCard(
                        vm = vm,
                        config = config,
                        diagnostics = stunDiag,
                        stunState = stunState,
                        serverRunning = serverState.isRunning,
                        onStart = { vm.startStun() },
                        onStop = { vm.stopStun() },
                        onCopy = { url -> vm.copyPublicUrl(context, url) }
                    )

                    FreeFrpPlatformsCard(context)
                    NetworkGuide()
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NetworkModeAndPrivacyCard(
    playMode: PlayConnectionMode,
    maskAddress: Boolean,
    onSelectMode: (PlayConnectionMode) -> Unit,
    onToggleMask: () -> Unit
) {
    McCard(title = "🎮 Chọn Chế Độ Chơi (Cùng Mạng vs. Kết Nối Mọi Nơi)") {
        Text(
            "Chọn đúng nhu cầu của bạn để hệ thống tự động tối ưu giao diện và kết nối:",
            color = Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isSame = playMode == PlayConnectionMode.SameNetwork
            val isEverywhere = playMode == PlayConnectionMode.Everywhere

            // Nút 1: Chơi Cùng Mạng
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSame) MintSoft else FieldBg)
                    .border(
                        BorderStroke(if (isSame) 2.dp else 1.dp, if (isSame) Mint else TrackBg),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectMode(PlayConnectionMode.SameNetwork) }
                    .padding(12.dp)
            ) {
                Text(
                    "🏠 Chơi Cùng Mạng",
                    color = if (isSame) Mint else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Chung Wi-Fi hoặc 1 máy bật Phát 4G Hotspot cho máy kia bắt. Siêu mượt, ping 1ms.",
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }

            // Nút 2: Bật Kết Nối Mọi Nơi
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isEverywhere) IndigoSoft else FieldBg)
                    .border(
                        BorderStroke(if (isEverywhere) 2.dp else 1.dp, if (isEverywhere) Indigo else TrackBg),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectMode(PlayConnectionMode.Everywhere) }
                    .padding(12.dp)
            ) {
                Text(
                    "🌍 Kết Nối Mọi Nơi",
                    color = if (isEverywhere) Indigo else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bạn bè ở xa dùng 4G/5G hoặc Wi-Fi khác nhà đều vào được từ bất kỳ đâu.",
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Hộp giải thích Bảo mật IP & nút Che địa chỉ khi quay video / chụp ảnh
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MintSoft.copy(alpha = 0.65f))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "🛡️ Bảo vệ 100% — KHÔNG lộ IP mạng nhà / 4G!",
                    color = Mint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onToggleMask,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        if (maskAddress) "👁️ Hiện địa chỉ" else "🙈 Che địa chỉ",
                        color = Indigo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (playMode == PlayConnectionMode.Everywhere) {
                    "Khi dùng Playit.gg Cloud, địa chỉ gửi cho bạn bè (*.gl.joinmc.link) là IP máy chủ đám mây quốc tế của Playit, hoàn toàn KHÔNG phải IP Wi-Fi nhà bạn hay sim 4G của bạn. Người lạ không thể tra vị trí hay DDoS mạng nhà bạn!"
                } else {
                    "Chế độ Cùng Mạng chỉ dùng địa chỉ nội bộ (192.168.x.x) bên trong cục Wi-Fi hoặc Hotspot của bạn, người ngoài Internet hoàn toàn không thể truy cập được."
                },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

@Composable
private fun PlayitEverywhereCard(
    vm: McViewModel,
    playitState: PlayitUiState,
    serverRunning: Boolean,
    serverPort: Int,
    maskAddress: Boolean,
    context: Context
) {
    var showSecretEditor by remember { mutableStateOf(false) }
    var manualSecretInput by remember { mutableStateOf("") }
    var showHowItWorks by remember { mutableStateOf(false) }

    val isRunning = playitState.isRunning
    val isBusy = playitState.stage == PlayitStage.WaitingForClaim ||
        playitState.stage == PlayitStage.VerifyingSecret ||
        playitState.stage == PlayitStage.CreatingTunnel ||
        playitState.stage == PlayitStage.ConnectingControl

    val statusColor = when (playitState.stage) {
        PlayitStage.Running -> if (playitState.isRunning) Mint else Coral
        PlayitStage.WaitingForClaim, PlayitStage.VerifyingSecret, PlayitStage.CreatingTunnel, PlayitStage.ConnectingControl -> Coral
        PlayitStage.Error -> Coral
        PlayitStage.Idle -> Muted
    }

    McCard(title = "🌍 Chơi Mọi Nơi Qua 4G / Khác Wi-Fi (Playit.gg Tích Hợp)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = statusColor)
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
            Spacer(Modifier.size(8.dp))
            StatusPill(playitState.statusText, statusColor)
            Spacer(Modifier.size(6.dp))
            Text("· Cổng đích $serverPort", color = Muted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(8.dp))
        HintNote(
            "Giải pháp khuyên dùng số 1 tại Việt Nam: Mở địa chỉ công khai miễn phí giúp bạn bè dùng 4G, 5G hoặc Wi-Fi khác nhà vào thẳng server mà KHÔNG cần cài thêm ứng dụng hay xác minh CCCD."
        )

        if (!serverRunning && !isRunning) {
            Spacer(Modifier.height(8.dp))
            WarnNote("💡 Lưu ý: Hãy bật máy chủ Minecraft (ở trang Tổng quan hoặc Quản lý) để người chơi kết nối vào được sau khi mở đường hầm.")
        }

        // Bước kích hoạt 1 lần trên trình duyệt khi chưa có Secret Key
        if (playitState.stage == PlayitStage.WaitingForClaim && playitState.claimUrl.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IndigoSoft)
                    .padding(12.dp)
            ) {
                Text(
                    "🔗 Kích hoạt 1 chạm (Chỉ cần làm 1 lần đầu tiên)",
                    color = Indigo,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "1. Bấm nút 'Mở trang kích hoạt Playit.gg' bên dưới.\n" +
                        "2. Trên web hiện ra, bấm 'Continue' / 'Add Agent' (có thể chọn Guest miễn phí, không cần tạo tài khoản).\n" +
                        "3. Quay lại ứng dụng MineHost — hệ thống sẽ tự động nhận khóa và tạo địa chỉ server cho bạn!",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(playitState.claimUrl)))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Mở trang kích hoạt", color = Color.White, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { vm.copyPublicUrl(context, playitState.claimUrl) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = Indigo, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Copy link", color = Indigo, fontSize = 12.sp)
                    }
                }
            }
        }

        if (playitState.errorMessage.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            WarnNote(playitState.errorMessage)
        }

        // Hiển thị Địa chỉ Công khai để gửi cho bạn bè (Cả dạng Chữ và dạng Toàn Số)
        val playitNumericAddress by vm.playitNumericAddress.collectAsState()
        if (playitState.publicAddress.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (isRunning) "✅ Địa chỉ công khai (Ẩn 100% IP nhà — Gửi cho bạn bè dùng 4G / Wi-Fi bất kỳ):"
                else "📌 Địa chỉ Playit.gg gần nhất của bạn (Hãy bấm Bật kết nối để hoạt động):",
                color = if (isRunning) Mint else Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            AddressCopyRow(
                label = "Địa chỉ Chữ (Playit.gg Domain)",
                value = PlayitTunnelController.maskEndpointForDisplay(playitState.publicAddress, maskAddress),
                onCopy = { vm.copyPublicUrl(context, playitState.publicAddress) },
                icon = Icons.Outlined.Public,
                accent = if (isRunning) Mint else Indigo,
                containerColor = if (isRunning) MintSoft else IndigoSoft,
                borderColor = if (isRunning) Mint else Indigo
            )
            val numericAddr = playitNumericAddress ?: playitState.secondaryAddress.takeIf { it.isNotBlank() && it != playitState.publicAddress }
            if (!numericAddr.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                AddressCopyRow(
                    label = "🔢 Địa chỉ IP Toàn Số Playit (Không lộ IP mạng hay máy)",
                    value = PlayitTunnelController.maskEndpointForDisplay(numericAddr, maskAddress),
                    onCopy = { vm.copyPublicUrl(context, numericAddr) },
                    icon = Icons.Outlined.Lan,
                    accent = Mint,
                    containerColor = MintSoft,
                    borderColor = Mint
                )
            }
        }

        if (isRunning) {
            Spacer(Modifier.height(10.dp))
            StatLine(
                Icons.Outlined.Link,
                "Đang có ${playitState.activeConnections} kết nối người chơi qua đường hầm Playit.gg"
            )
        }

        Spacer(Modifier.height(12.dp))
        if (!serverRunning && !isRunning && !isBusy) {
            Button(
                onClick = { vm.startServerWithMode(PlayConnectionMode.Everywhere) },
                colors = ButtonDefaults.buttonColors(containerColor = Mint),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("🚀 Bật Cả Máy Chủ + Kết Nối Mọi Nơi (1 Chạm)", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
        TunnelActionRow(
            startText = if (playitState.hasSavedSecret) "🌐 Chỉ bật đường hầm" else "🌐 Kích hoạt Playit",
            stopText = "⏹️ Dừng Playit.gg",
            startEnabled = !isRunning && !isBusy,
            stopEnabled = isRunning || isBusy,
            onStart = { vm.startPlayitTunnel() },
            onStop = { vm.stopPlayitTunnel() }
        )

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showHowItWorks = !showHowItWorks }) {
                Icon(
                    if (showHowItWorks) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Indigo,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text("Hướng dẫn bạn bè vào server", color = Indigo, fontSize = 11.sp)
            }
            TextButton(onClick = { showSecretEditor = !showSecretEditor }) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(4.dp))
                Text("Quản lý khóa Agent", color = Muted, fontSize = 11.sp)
            }
        }

        if (showHowItWorks) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(FieldBg)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("📖 Cách để bạn bè ở xa (dùng 4G hoặc Wi-Fi khác) vào chơi:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("• Bước 1: Bạn khởi động máy chủ Minecraft ở trang Tổng quan/Quản lý (nếu bạn bè dùng tài khoản Cracked/Offline, nhớ tắt chế độ Bản quyền online-mode=false).", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                Text("• Bước 2: Bấm '🚀 Bật kết nối Mọi Nơi' ở trên. Sao chép địa chỉ công khai (ví dụ: xxx.gl.joinmc.link).", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                Text("• Bước 3: Bạn bè mở Minecraft (trên điện thoại 4G/Wi-Fi hoặc PC) -> Chơi nhiều người (Multiplayer) -> Kết nối trực tiếp / Thêm máy chủ -> Dán địa chỉ đó vào là vào chơi ngay!", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }

        if (showSecretEditor) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(FieldBg)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔑 Nhập Agent Secret Key thủ công hoặc đặt lại liên kết:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = manualSecretInput,
                    onValueChange = { manualSecretInput = it },
                    placeholder = { Text("Dán Agent Secret Key (hex) nếu đã có sẵn...", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.setPlayitSecretKey(manualSecretInput)
                            manualSecretInput = ""
                            showSecretEditor = false
                        },
                        enabled = manualSecretInput.trim().length >= 24,
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lưu & Kết nối", color = Color.White, fontSize = 11.sp)
                    }
                    if (playitState.hasSavedSecret) {
                        OutlinedButton(
                            onClick = {
                                vm.resetPlayitSecretKey()
                                showSecretEditor = false
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Xóa khóa cũ", color = Coral, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualLanCard(
    serverPort: Int,
    endpoints: List<VirtualLanEndpoint>,
    maskAddress: Boolean,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    context: Context
) {
    var expanded by remember { mutableStateOf(false) }

    McCard(title = "⚡ Mạng LAN Ảo P2P (ZeroTier / Tailscale — Ping cực thấp cho Map Mod)") {
        Text(
            "Nếu bạn host Map có nhiều Mod nặng và muốn đường truyền trực tiếp giữa các máy tại Việt Nam (ping 15–30ms, dùng được cả 4G lẫn Wi-Fi, bảo mật mã hóa đầu-cuối không lộ IP nhà), hãy bật ZeroTier One hoặc Tailscale.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))

        if (endpoints.isNotEmpty()) {
            Text(
                "✅ Đã phát hiện địa chỉ Mạng LAN Ảo đang bật trên máy của bạn:",
                color = Mint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            endpoints.forEach { ep ->
                val fullAddr = "${ep.ip}:$serverPort"
                AddressCopyRow(
                    label = "${ep.providerLabel} (${ep.interfaceName})",
                    value = PlayitTunnelController.maskEndpointForDisplay(fullAddr, maskAddress),
                    onCopy = { onCopy(fullAddr) },
                    icon = Icons.Outlined.Lan,
                    accent = Mint,
                    containerColor = MintSoft,
                    borderColor = Mint
                )
                Spacer(Modifier.height(6.dp))
            }
        } else {
            HintNote("Hiện chưa phát hiện mạng ảo ZeroTier / Tailscale nào đang bật trên điện thoại. Sau khi bật VPN mạng ảo, hãy bấm 'Quét lại IP Mạng Ảo'.")
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRefresh,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Indigo, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Quét lại IP Mạng Ảo", color = Indigo, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Indigo,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(if (expanded) "Thu gọn hướng dẫn" else "Cách dùng ZeroTier", color = Indigo, fontSize = 12.sp)
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(FieldBg)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("📖 Hướng dẫn chơi qua 4G / Khác Wi-Fi bằng ZeroTier One hoặc Tailscale:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("1. Cả bạn và bạn bè tải ứng dụng 'ZeroTier One' hoặc 'Tailscale' (miễn phí trên CH Play / App Store / PC).", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                Text("2. Cùng tham gia 1 phòng mạng ảo (Network ID 16 số của ZeroTier hoặc đăng nhập chung nhóm Tailscale) và gạt công tắc BẬT.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                Text("3. Bấm nút 'Quét lại IP Mạng Ảo' ở trên -> Sao chép địa chỉ IP:Port hiện ra gửi cho bạn bè nhập vào Minecraft là vào thẳng!", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://my.zerotier.com/"))) }
                    }) {
                        Text("🌐 Mở ZeroTier Central", color = Indigo, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tailscale.com/download"))) }
                    }) {
                        Text("🌐 Tải Tailscale", color = Indigo, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun statusVisual(status: TunnelStatus): Triple<Color, String, Color> = when (status) {
    TunnelStatus.Running -> Triple(Mint, stringResource(R.string.net_status_running), Mint)
    TunnelStatus.Starting -> Triple(Coral, stringResource(R.string.net_status_starting), Coral)
    TunnelStatus.Failed -> Triple(Coral, stringResource(R.string.net_status_failed), Coral)
    TunnelStatus.Stopped -> Triple(Muted, stringResource(R.string.net_status_stopped), Muted)
    TunnelStatus.Idle -> Triple(Muted, stringResource(R.string.net_status_idle), Muted)
}

@Composable
private fun TunnelStatusHeader(status: TunnelStatus, typeLabel: String?) {
    val (dotColor, text, color) = statusVisual(status)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == TunnelStatus.Starting) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Spacer(Modifier.size(8.dp))
        StatusPill(text, color)
        if (!typeLabel.isNullOrBlank()) {
            Spacer(Modifier.size(6.dp))
            Text("·", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.size(6.dp))
            Text(typeLabel, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TunnelActionRow(
    startText: String,
    stopText: String,
    startEnabled: Boolean,
    stopEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStart,
            enabled = startEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Indigo,
                disabledContainerColor = TrackBg
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(startText, color = Color.White)
        }
        Button(
            onClick = onStop,
            enabled = stopEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Muted,
                disabledContainerColor = TrackBg
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Outlined.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stopText, color = Color.White)
        }
    }
}

@Composable
private fun TunnelPreconditionNotes(state: TunnelState, serverRunning: Boolean) {
    if (!serverRunning && state.status != TunnelStatus.Running) {
        Spacer(Modifier.height(10.dp))
        WarnNote(stringResource(R.string.net_tunnel_server_stopped))
    }
    if (state.status == TunnelStatus.Failed && state.errorMessage.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        WarnNote(state.errorMessage)
    }
    if (state.status == TunnelStatus.Running && state.publicUrl.isBlank()) {
        Spacer(Modifier.height(10.dp))
        WarnNote(stringResource(R.string.net_public_url_unknown))
    }
}

@Composable
private fun PublicAddressBlock(
    label: String,
    url: String,
    state: TunnelState,
    onCopy: () -> Unit
) {
    if (state.status != TunnelStatus.Running || url.isBlank()) return
    Spacer(Modifier.height(12.dp))
    AddressCopyRow(
        label = label,
        value = url,
        onCopy = onCopy,
        accent = Mint,
        containerColor = MintSoft,
        borderColor = Mint
    )
}

@Composable
private fun LocalAddressCard(
    vm: McViewModel,
    serverPort: Int,
    lanIp: String,
    serverRunning: Boolean,
    maskAddress: Boolean,
    context: Context
) {
    val lanInterfaceLabel by vm.lanInterfaceLabel.collectAsState()
    val lanLastUpdated by vm.lanLastUpdatedText.collectAsState()
    val vpnActiveWarning by vm.vpnActiveWarning.collectAsState()
    val connectionCheckStatus by vm.connectionCheckStatus.collectAsState()
    val connectionCheckRunning by vm.connectionCheckRunning.collectAsState()

    McCard(title = "🏠 Địa chỉ Chơi Cùng Mạng (Wi-Fi / Phát 4G Hotspot) & Localhost") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusPill(
                if (serverRunning) stringResource(R.string.s587) else stringResource(R.string.s280),
                if (serverRunning) Mint else Muted
            )
            if (!serverRunning) {
                Button(
                    onClick = { vm.startServerWithMode(PlayConnectionMode.SameNetwork) },
                    colors = ButtonDefaults.buttonColors(containerColor = Mint),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("▶️ Bật máy chủ Cùng Mạng", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("1️⃣ Địa chỉ LAN dành cho bạn bè bắt chung Wi-Fi hoặc 4G Hotspot của bạn:", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val hasValidLan = lanIp != "--" && lanIp.isNotBlank()
        val lanAddressDisplay = if (hasValidLan) {
            PlayitTunnelController.maskEndpointForDisplay("$lanIp:$serverPort", maskAddress)
        } else {
            "Chưa xác định được địa chỉ mạng LAN"
        }
        AddressCopyRow(
            label = "${stringResource(R.string.s592)} • $lanInterfaceLabel",
            value = lanAddressDisplay,
            onCopy = { vm.copyServerAddress(context) },
            icon = Icons.Outlined.Lan,
            accent = if (hasValidLan) Mint else Coral,
            containerColor = if (hasValidLan) MintSoft else FieldBg
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildString {
                    append("Giao diện: $lanInterfaceLabel • Cập nhật lúc: $lanLastUpdated\n")
                    if (!serverRunning && hasValidLan) {
                        append("⚠️ Đã có IP LAN nhưng Server đang dừng — Hãy bật Server trước khi kết nối.")
                    } else {
                        append(stringResource(R.string.s594))
                    }
                },
                color = Muted, fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.refreshLanIp(notifyOnChange = true) }, modifier = Modifier.size(McIcon.TouchCompact)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.s591),
                    tint = Indigo,
                    modifier = Modifier.size(McIcon.Inline)
                )
            }
        }
        if (vpnActiveWarning) {
            Spacer(Modifier.height(6.dp))
            WarnNote("⚠️ Phát hiện VPN đang bật trên điện thoại: VPN có thể chuyển hướng lưu lượng và làm bạn bè trong cùng Wi-Fi không kết nối được vào địa chỉ LAN.")
        }

        Spacer(Modifier.height(12.dp))

        Text("2️⃣ Địa chỉ Localhost dành riêng cho chính chiếc điện thoại này:", color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        AddressCopyRow(
            label = stringResource(R.string.s588),
            value = "127.0.0.1:$serverPort",
            onCopy = { copyToClipboard(context, "MC Server", "127.0.0.1:$serverPort") }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Chỉ dùng 127.0.0.1:$serverPort khi bạn mở Minecraft ngay trên chính chiếc điện thoại đang chạy MineHost này. Bạn bè trên máy khác KHÔNG dùng địa chỉ này.",
            color = Muted,
            fontSize = 10.sp
        )

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { vm.checkConnectionReachability() },
            enabled = !connectionCheckRunning,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (connectionCheckRunning) "⏳ Đang kiểm tra khả năng kết nối..."
                else "🩺 Kiểm tra kết nối thực tế (Localhost / LAN / Playit.gg)",
                color = Indigo,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        connectionCheckStatus?.let { statusReport ->
            Spacer(Modifier.height(8.dp))
            HintNote(statusReport)
        }

        Spacer(Modifier.height(8.dp))
        HintNote("📶 Cách chơi bằng 4G không cần Wi-Fi khi ngồi gần nhau:\n• Bước 1: Mở 'Điểm truy cập cá nhân' (Phát Wi-Fi từ 4G) trên điện thoại cho các máy khác kết nối vào.\n• Bước 2: Bấm nút 🔄 Làm mới ở trên để lấy địa chỉ LAN nội bộ (192.168.x.x) gửi cho bạn bè — tốc độ cực nhanh, không tốn dung lượng trung gian và tuyệt đối không lộ IP ra ngoài!")
    }
}

@Composable
private fun FrpCard(
    vm: McViewModel,
    config: McConfig,
    frpState: TunnelState,
    serverRunning: Boolean,
    serverPort: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenEditor: (String?) -> Unit,
    onCopy: (String) -> Unit
) {
    val profile = FrpProfiles.active(config)
    var showPicker by remember { mutableStateOf(false) }

    McCard(title = stringResource(R.string.net_frp_card_title)) {
        TunnelStatusHeader(frpState.status, frpState.activeType?.displayName)
        Spacer(Modifier.height(12.dp))

        if (profile == null) {
            HintNote(stringResource(R.string.net_frp_no_profile))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onOpenEditor(null) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = Indigo, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.net_frp_create), color = Indigo, fontSize = 12.sp)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FieldBg)
                    .clickable { showPicker = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.net_frp_profile_label),
                        color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        FrpProfiles.label(profile),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.net_frp_switch_hint),
                    tint = Indigo,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (profile != null &&
            profile.kind == FrpProfileKind.CustomToml &&
            frpState.status != TunnelStatus.Running
        ) {
            val target = FrpLocalTarget.target(profile.configText)
            if (!target.foreignHost) {
                val only = target.only
                when {
                    target.ports.isNotEmpty() && !target.single -> {
                        Spacer(Modifier.height(8.dp))
                        WarnNote(stringResource(R.string.net_frp_port_ambiguous, serverPort))
                    }
                    only != null && only != serverPort -> {
                        Spacer(Modifier.height(8.dp))
                        WarnNote(stringResource(R.string.net_frp_port_mismatch, only, serverPort))
                    }
                }
            }
        }

        TunnelPreconditionNotes(frpState, serverRunning)
        PublicAddressBlock(
            label = stringResource(R.string.net_frp_address),
            url = frpState.publicUrl,
            state = frpState
        ) { onCopy(frpState.publicUrl) }

        Spacer(Modifier.height(12.dp))
        TunnelActionRow(
            startText = stringResource(R.string.net_frp_start),
            stopText = stringResource(R.string.net_frp_stop),
            startEnabled = frpState.status != TunnelStatus.Starting && profile != null,
            stopEnabled = frpState.status == TunnelStatus.Running ||
                frpState.status == TunnelStatus.Starting,
            onStart = onStart,
            onStop = onStop
        )
    }

    if (showPicker) {
        FrpProfilePickerDialog(
            vm = vm,
            config = config,
            activeId = profile?.id,
            onDismiss = { showPicker = false },
            onOpenEditor = { id ->
                showPicker = false
                onOpenEditor(id)
            }
        )
    }
}

@Composable
private fun FrpProfilePickerDialog(
    vm: McViewModel,
    config: McConfig,
    activeId: String?,
    onDismiss: () -> Unit,
    onOpenEditor: (String?) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<FrpProfile?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.net_frp_dialog_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                config.frpProfiles.forEach { p ->
                    val selected = p.id == activeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) IndigoSoft else Color.Transparent)
                            .clickable {
                                vm.setActiveFrpProfile(p.id)
                                onDismiss()
                            }
                            .padding(start = 4.dp, end = 0.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = {
                                vm.setActiveFrpProfile(p.id)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Indigo)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                if (p.tunnelLabel.isBlank()) p.kind.displayName
                                else "${p.kind.displayName} · ${p.tunnelLabel}",
                                color = Muted, fontSize = 10.sp, maxLines = 2
                            )
                        }
                        IconButton(onClick = { onOpenEditor(p.id) }, modifier = Modifier.size(McIcon.TouchCompact)) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.net_frp_dialog_edit),
                                tint = Indigo, modifier = Modifier.size(McIcon.Inline)
                            )
                        }
                        IconButton(onClick = { pendingDelete = p }, modifier = Modifier.size(McIcon.TouchCompact)) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.net_frp_dialog_delete),
                                tint = Coral, modifier = Modifier.size(McIcon.Inline)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onOpenEditor(null) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = Indigo, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.net_frp_create), color = Indigo, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.s402)) }
        }
    )

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.net_frp_delete_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.net_frp_delete_message, target.name), fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteFrpProfile(target.id)
                        pendingDelete = null
                        if (vm.config.value.frpProfiles.isEmpty()) onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    shape = McShape.Control
                ) { Text(stringResource(R.string.net_frp_dialog_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.s402)) }
            }
        )
    }
}

@Composable
private fun StunCard(
    vm: McViewModel,
    config: McConfig,
    diagnostics: StunDiagnostics,
    stunState: TunnelState,
    serverRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopy: (String) -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }
    val address = diagnostics.publicAddress.ifBlank { stunState.publicUrl }

    McCard(title = stringResource(R.string.stun_card_title)) {
        TunnelStatusHeader(stunState.status, null)
        Spacer(Modifier.height(10.dp))
        WarnNote("⚠️ Lưu ý bảo mật IP: Khác với Playit.gg (ẩn IP nhà 100% qua đám mây), tính năng STUN đục lỗ NAT trực tiếp nên địa chỉ tạo ra sẽ chứa IP WAN thật của mạng nhà bạn. Chỉ nên gửi địa chỉ STUN cho bạn bè thân thiết, hoặc dùng Playit.gg ở trên để ẩn IP tuyệt đối!")

        TunnelPreconditionNotes(stunState, serverRunning)

        if (stunState.status == TunnelStatus.Running && address.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            AddressCopyRow(
                label = stringResource(R.string.stun_card_address),
                value = address,
                onCopy = { onCopy(address) },
                accent = Mint,
                containerColor = MintSoft,
                borderColor = Mint
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (diagnostics.portStable) stringResource(R.string.stun_card_address_hint)
                else stringResource(R.string.stun_card_address_drift, diagnostics.addressChanges),
                color = Muted, fontSize = 10.sp
            )
        } else if (stunState.status != TunnelStatus.Failed) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.net_stun_idle_hint), color = Muted, fontSize = 11.sp)
        }

        if (stunState.status == TunnelStatus.Running) {
            Spacer(Modifier.height(12.dp))
            StatLine(
                Icons.Outlined.Link,
                stringResource(R.string.stun_stat_conn, diagnostics.activeConnections)
            )
            if (diagnostics.idleTimeouts > 0) {
                StatLine(
                    Icons.Outlined.Info,
                    stringResource(R.string.stun_stat_idle, diagnostics.idleTimeouts)
                )
            }
            if (diagnostics.limitRejects > 0) {
                StatLine(
                    Icons.Outlined.DataUsage,
                    stringResource(R.string.stun_stat_limit, diagnostics.limitRejects)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        TunnelActionRow(
            startText = stringResource(R.string.stun_card_start),
            stopText = stringResource(R.string.stun_card_stop),
            startEnabled = stunState.status != TunnelStatus.Starting,
            stopEnabled = stunState.status == TunnelStatus.Running ||
                stunState.status == TunnelStatus.Starting,
            onStart = onStart,
            onStop = onStop
        )

        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (showAdvanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null, tint = Indigo, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                if (showAdvanced) stringResource(R.string.stun_advanced_hide)
                else stringResource(R.string.stun_advanced),
                color = Indigo, fontSize = 12.sp
            )
        }
        if (showAdvanced) {
            StunConfigSection(vm = vm, config = config)
        }
    }
}

@Composable
private fun StunToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}

@Composable
private fun StunConfigSection(vm: McViewModel, config: McConfig) {
    val serverPort by vm.serverPort.collectAsState()
    val followLocalPort = config.stunLocalPort <= 0
    Column {
        Text(stringResource(R.string.stun_target_port), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = followLocalPort,
                onCheckedChange = { follow ->
                    vm.updateConfig { it.copy(stunLocalPort = if (follow) 0 else serverPort) }
                }
            )
            Text(
                stringResource(R.string.stun_follow_local_port, serverPort),
                color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp
            )
        }
        if (!followLocalPort) {
            Spacer(Modifier.height(6.dp))
            DebouncedTextField(
                value = config.stunLocalPort.toString(),
                onValueChange = { v ->
                    val port = v.filter(Char::isDigit).take(5).toIntOrNull() ?: 0
                    vm.updateConfig { it.copy(stunLocalPort = port) }
                },
                placeholder = { Text(stringResource(R.string.stun_target_port_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.stun_keepalive), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        DebouncedTextField(
            value = config.stunKeepAliveSeconds.toString(),
            onValueChange = { v ->
                val secs = v.filter(Char::isDigit).take(4).toIntOrNull() ?: 20
                vm.updateConfig { it.copy(stunKeepAliveSeconds = secs) }
            },
            placeholder = { Text("20") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        HintNote(stringResource(R.string.stun_keepalive_hint))

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.stun_max_conn), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        DebouncedTextField(
            value = config.stunMaxConnections.toString(),
            onValueChange = { v ->
                val n = v.filter(Char::isDigit).take(5).toIntOrNull() ?: 128
                vm.updateConfig { it.copy(stunMaxConnections = n) }
            },
            placeholder = { Text("128") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        StunToggleRow(
            title = stringResource(R.string.stun_show_conn_log),
            checked = config.stunShowConnectionLog,
            onChange = { on -> vm.updateConfig { it.copy(stunShowConnectionLog = on) } }
        )

        Spacer(Modifier.height(4.dp))
        StunToggleRow(
            title = stringResource(R.string.stun_proxy_protocol),
            checked = config.stunProxyProtocol,
            onChange = { on -> vm.updateConfig { it.copy(stunProxyProtocol = on) } }
        )
        if (config.stunProxyProtocol) {
            Spacer(Modifier.height(6.dp))
            WarnNote(stringResource(R.string.stun_proxy_protocol_warn))
        }

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.stun_servers), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        DebouncedTextField(
            value = config.stunServers,
            onValueChange = { v -> vm.updateConfig { it.copy(stunServers = v) } },
            placeholder = { Text(stringResource(R.string.stun_servers_hint)) },
            singleLine = false,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        HintNote(stringResource(R.string.stun_servers_note))
    }
}

@Composable
private fun TunnelLogPreview(
    consoleLines: List<String>,
    frpState: TunnelState,
    stunState: TunnelState,
    playitState: PlayitUiState
) {
    val tunnelLogs: List<String> = remember(consoleLines, playitState.recentLogs) {
        val fromConsole = consoleLines.filter { it.contains("[tunnel]") }
        if (fromConsole.isNotEmpty()) fromConsole
        else playitState.recentLogs.map { "[tunnel] [Playit] $it" }
    }

    var showFullLog by remember { mutableStateOf(false) }
    val activeConnCount = when {
        playitState.stage == PlayitStage.Running -> playitState.activeConnections
        frpState.status == TunnelStatus.Running -> frpState.activeConnections
        stunState.status == TunnelStatus.Running -> stunState.activeConnections
        else -> null
    }

    McCard(title = stringResource(R.string.net_section_log)) {
        if (tunnelLogs.isEmpty()) {
            Text(stringResource(R.string.net_log_empty), color = Muted, fontSize = 11.sp)
        } else {
            tunnelLogs.takeLast(8).forEach { line ->
                Text(
                    line,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Muted,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showFullLog = true },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.OpenInFull,
                    contentDescription = null,
                    tint = Indigo,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.net_log_expand) + " (${tunnelLogs.size})",
                    fontSize = 12.sp,
                    color = Indigo
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.net_log_connection),
            color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        if (activeConnCount != null) {
            StatLine(
                Icons.Outlined.Link,
                stringResource(R.string.net_stats_connections, activeConnCount)
            )
            Spacer(Modifier.height(6.dp))
        }
        HintNote(stringResource(R.string.net_log_connection_hint))
    }

    if (showFullLog) {
        AlertDialog(
            onDismissRequest = { showFullLog = false },
            title = {
                Text(stringResource(R.string.s618, tunnelLogs.size), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (tunnelLogs.isEmpty()) {
                        Text(stringResource(R.string.s619), color = Muted, fontSize = 13.sp)
                    } else {
                        tunnelLogs.forEach { line ->
                            Text(
                                line,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Muted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFullLog = false }, shape = McShape.Control) {
                    Text(stringResource(R.string.s620))
                }
            }
        )
    }
}

private data class FrpPlatform(val name: String, val url: String)

private val freeFrpPlatforms = listOf(
    FrpPlatform("Playit.gg (Quốc tế - Miễn phí, khuyên dùng số 1 tại VN)", "https://playit.gg/"),
    FrpPlatform("ZeroTier Central (Mạng LAN ảo P2P ping thấp cho Map Mod)", "https://my.zerotier.com/"),
    FrpPlatform("Tailscale (Mạng P2P tốc độ cao đa nền tảng)", "https://tailscale.com/"),
    FrpPlatform("OpenFrp", "https://www.openfrp.net/"),
    FrpPlatform("ChmlFrp", "https://www.chmlfrp.cn/"),
    FrpPlatform("StarryFrp", "https://frp.starryfrp.com/"),
    FrpPlatform("SakuraFrp (NatFrp)", "https://www.natfrp.com/")
)

@Composable
private fun FreeFrpPlatformsCard(context: Context) {
    McCard(title = stringResource(R.string.s654)) {
        Text(
            stringResource(R.string.s655),
            color = Muted, fontSize = 10.sp, lineHeight = 14.sp
        )
        Spacer(Modifier.height(10.dp))
        freeFrpPlatforms.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IndigoSoft)
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.url)))
                        } catch (_: Exception) {
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(p.url, color = Indigo, fontSize = 10.sp)
                }
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(R.string.s656),
                    tint = Indigo,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun NetworkGuide() {
    var expanded by remember { mutableStateOf(false) }

    McCard(title = stringResource(R.string.s621)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.s622, if (expanded) stringResource(R.string.s620) else stringResource(R.string.s623)),
                color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = Indigo
            )
        }

        if (expanded) {
            Column {
                Text(
                    stringResource(R.string.net_guide_intro),
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                GuideStep("1", stringResource(R.string.net_guide_frp_t), stringResource(R.string.net_guide_frp_d))
                GuideStep("2", stringResource(R.string.net_guide_switch_t), stringResource(R.string.net_guide_switch_d))
                GuideStep("3", stringResource(R.string.net_guide_frp_run_t), stringResource(R.string.net_guide_frp_run_d))
                GuideStep("4", stringResource(R.string.net_guide_stun_t), stringResource(R.string.net_guide_stun_d))
                GuideStep("5", stringResource(R.string.net_guide_both_t), stringResource(R.string.net_guide_both_d))
                GuideStep("6", stringResource(R.string.net_guide_stop_t), stringResource(R.string.net_guide_stop_d))
            }
        }
    }
}

@Composable
private fun GuideStep(step: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Indigo),
            contentAlignment = Alignment.Center
        ) {
            Text(step, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
