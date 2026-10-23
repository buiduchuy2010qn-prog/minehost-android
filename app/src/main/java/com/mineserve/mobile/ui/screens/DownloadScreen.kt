package com.mineserve.mobile.ui.screens

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.mineserve.mobile.R
import com.mineserve.mobile.ui.showInstantSnackbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.data.InstalledCore
import com.mineserve.mobile.data.JavaRuntime
import com.mineserve.mobile.data.JavaVendor
import com.mineserve.mobile.data.JavaVersion
import com.mineserve.mobile.ui.BackBar
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegPill
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.IndigoSoft
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.Muted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private data class DownloadImport(val kind: String, val uri: Uri)

/**
 * 下载页：选择服务端核心 + 选择游戏版本 + 下载服务端 JAR
 * 核心类型和版本均从官方 API 动态获取，确保下载源正确
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadScreen(vm: McViewModel, onShowDownloadHelp: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val config by vm.config.collectAsState()
    val serverState by vm.serverState.collectAsState()
    val isBootstrapped by vm.isBootstrapped.collectAsState()
    val availableVersions by vm.availableVersions.collectAsState()
    val versionHints by vm.versionHints.collectAsState()
    val isLoadingVersions by vm.isLoadingVersions.collectAsState()
    val isDownloadingCore by vm.isDownloadingCore.collectAsState()
    val downloadProgress by vm.downloadProgress.collectAsState()
    val consoleLines by vm.consolePreviewLines.collectAsState()
    val importingServer by vm.isImportingServer.collectAsState()
    val importProgress by vm.importProgress.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val usesCoreVersion = config.selectedCore.isBedrock
    val storageMap by vm.serverStorageMap.collectAsState()
    val installedJava by vm.installedJava.collectAsState()
    val javaOperation by vm.javaOperation.collectAsState()
    var versionBranchFilter by rememberSaveable { mutableStateOf("all") }

    // 自定义版本输入框
    var customVersion by remember { mutableStateOf("") }
    // 自定义核心名称
    var customCoreName by remember { mutableStateOf("") }
    var wantsAdditionalServer by remember(config.selectedCore, config.mcVersion) { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<DownloadImport?>(null) }
    var importName by remember { mutableStateOf("") }
    var importObservedRunning by remember { mutableStateOf(false) }
    var showAllVersions by remember { mutableStateOf(false) }
    // Các tùy chọn thiết lập Server linh hoạt (không khóa cứng phiên bản mặc định)
    var flexibleGamemode by rememberSaveable { mutableStateOf("survival") }
    var flexibleCrackedMode by rememberSaveable { mutableStateOf(true) }
    var flexibleCrossplayEnabled by rememberSaveable { mutableStateOf(false) }
    var flexibleBedrockBranch by rememberSaveable { mutableStateOf("1.21") }
    var crossplayRefreshKey by remember { mutableStateOf(0) }
    val crossplayWanted = flexibleCrossplayEnabled && config.selectedCore.supportsPlugins && !config.selectedCore.isBedrock
    // Kiểm tra THẬT: Java ${config.mcVersion} chơi chung được với Minecraft PE bản nào (theo GeyserMC hiện hành)
    val crossplayCheck = com.mineserve.mobile.ui.rememberCrossplayCheck(
        core = config.selectedCore,
        mcVersion = config.mcVersion,
        enabled = crossplayWanted,
        refreshKey = crossplayRefreshKey,
        targetPe = flexibleBedrockBranch
    )
    val crossplayBlocked = crossplayWanted && crossplayCheck?.supported != true
    val freeStorageMb = remember { (context.filesDir.usableSpace / (1024L * 1024L)).coerceAtLeast(0L) }
    var showSoftwareComparison by rememberSaveable { mutableStateOf(false) }
    var installedExpanded by rememberSaveable { mutableStateOf(true) }
    var showMoreCores by rememberSaveable { mutableStateOf(false) }
    val installedCoreCounts = remember(config.installedCores) {
        config.installedCores.groupingBy { it.core }.eachCount()
    }
    val installedVersionsForSelectedCore = remember(config.installedCores, config.selectedCore) {
        config.installedCores.asSequence()
            .filter { it.core == config.selectedCore }
            .map { it.version }
            .toHashSet()
    }
    val installedBedrockCoreVersions = remember(config.installedCores) {
        config.installedCores.asSequence()
            .filter { it.core.isBedrock }
            .map { it.core to it.version.substringBefore("@") }
            .toHashSet()
    }
    val cleanSelectedCoreVer = remember(config.mcVersion) {
        config.mcVersion.substringBefore("@")
    }
    val selectedPeHint = remember(usesCoreVersion, config.mcVersion, versionHints) {
        if (!usesCoreVersion) null
        else versionHints[config.mcVersion]
            ?: config.mcVersion.substringAfter("@", missingDelimiterValue = "").ifBlank { null }
            ?: versionHints[cleanSelectedCoreVer]
    }
    val displaySelectedVersion = remember(usesCoreVersion, selectedPeHint, cleanSelectedCoreVer, config.mcVersion) {
        if (usesCoreVersion && !selectedPeHint.isNullOrBlank()) {
            "PE $selectedPeHint ($cleanSelectedCoreVer)"
        } else {
            cleanSelectedCoreVer
        }
    }
    val unifiedBedrockEntries = remember(config.selectedCore, availableVersions, versionHints, usesCoreVersion) {
        if (usesCoreVersion) {
            com.mineserve.mobile.server.McServerController.buildUnifiedBedrockCatalog(
                selectedCore = config.selectedCore,
                nativeVersions = availableVersions,
                nativeHints = versionHints
            )
        } else {
            emptyList()
        }
    }
    var showVersionPickerModal by remember { mutableStateOf(false) }
    fun triggerQuickInstallForVersion(
        targetCore: ServerCore,
        targetVerKey: String,
        peLabel: String? = null
    ) {
        val cleanVer = targetVerKey.substringBefore("@")
        val existingMatch = config.installedCores.firstOrNull {
            it.core == targetCore && (it.version == targetVerKey || it.version == cleanVer)
        }
        if (existingMatch != null && !wantsAdditionalServer) {
            vm.setActiveCore(existingMatch.name)
            scope.launch {
                snackbarHostState.showInstantSnackbar("🚀 Đã chọn máy chủ có sẵn: ${existingMatch.name}")
            }
            onBack?.invoke()
        } else if (!isBootstrapped) {
            scope.launch {
                snackbarHostState.showInstantSnackbar("⚠️ Vui lòng đợi khởi tạo môi trường Termux hoàn tất trước khi tải!")
            }
        } else if (isDownloadingCore) {
            scope.launch {
                snackbarHostState.showInstantSnackbar("⏳ Đang tải một máy chủ khác, vui lòng đợi hoàn tất!")
            }
        } else {
            val slug = if (targetCore.isBedrock && !peLabel.isNullOrBlank()) "PE-$peLabel" else cleanVer
            val autoName = customCoreName.trim().ifBlank {
                when {
                    flexibleCrossplayEnabled && !targetCore.isBedrock -> "Crossplay-${targetCore.displayName}-$slug"
                    flexibleGamemode == "creative" -> "SangTao-${targetCore.displayName}-$slug"
                    flexibleGamemode == "hardcore" -> "Hardcore-${targetCore.displayName}-$slug"
                    else -> "${targetCore.displayName}-$slug"
                }
            }
            vm.downloadCore(
                customName = autoName,
                initialGamemode = flexibleGamemode,
                crackedOfflineMode = flexibleCrackedMode,
                installCrossplayPack = flexibleCrossplayEnabled && !targetCore.isBedrock,
                crossplayBedrockBranch = flexibleBedrockBranch,
                overrideCore = targetCore,
                overrideVersion = targetVerKey,
                autoResolveDuplicateName = true
            )
            scope.launch {
                snackbarHostState.showInstantSnackbar("⬇️ Đang tải & cài đặt máy chủ $autoName...")
            }
        }
    }
    // 待删除的已安装核心（二次确认；删除会连同该目录下的世界、插件与配置一起移除）
    var pendingDeleteCore by remember { mutableStateOf<InstalledCore?>(null) }
    val folderImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { pendingImport = DownloadImport("folder", it); showImportDialog = true }
    }
    val archiveImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingImport = DownloadImport("archive", it); showImportDialog = true }
    }
    val jarImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingImport = DownloadImport("jar", it); showImportDialog = true }
    }
    val modpackImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingImport = DownloadImport("modpack", it); showImportDialog = true }
    }
    LaunchedEffect(pendingImport) {
        pendingImport?.let { importName = vm.proposeImportName(it.kind, it.uri).orEmpty() }
    }
    LaunchedEffect(importingServer) {
        if (importingServer) {
            importObservedRunning = true
        } else if (importObservedRunning) {
            showImportDialog = false
            pendingImport = null
            importObservedRunning = false
        }
    }
    // 切换核心时自动加载版本列表
    LaunchedEffect(config.selectedCore) {
        showAllVersions = config.selectedCore.isBedrock
        versionBranchFilter = "all"
        vm.loadVersions(config.selectedCore)
    }

    // 收集消息
    LaunchedEffect(Unit) {
        vm.refreshServerStorage()
        vm.refreshJava()
        vm.messageFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.errorFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.coreDownloadCompletedFlow.collectLatest {
            onBack?.invoke()
        }
    }

    Scaffold(
        snackbarHost = { com.mineserve.mobile.ui.SwipeableInstantSnackbarHost(snackbarHostState) },
        // 嵌套在外层 McApp Scaffold 内：insets 已由外层消费，这里不再重复应用，避免顶部空白/白色遮挡
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (onBack != null) {
                BackBar(title = stringResource(R.string.s450), onBack = onBack)
            } else {
                HeaderBlock(eyebrow = stringResource(R.string.eyebrow_download), title = stringResource(R.string.s450))
            }
            val gamemodeLabelVi = when (flexibleGamemode) {
                "creative" -> "🎨 Sáng tạo"
                "adventure" -> "🗺️ Phiêu lưu"
                "hardcore" -> "💀 Siêu khó"
                else -> "🌱 Sinh tồn"
            }

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
            item(key = "flexible_configurator") {
                McCard(title = "🛠️ Thiết Lập Server Linh Hoạt (Tự chọn Core • Phiên bản • Chế độ • PC+PE)") {
                    Text(
                        "Tự do kết hợp bất kỳ Phần mềm (Core), bất kỳ Phiên bản Minecraft (1.7.10 → 1.21.11 / 26.x), Chế độ chơi và Chơi chung PC + PE mà không bị gò bó theo mẫu cố định:",
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    // Khung tóm tắt cấu hình đang ghép trực tiếp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(McShape.Row)
                            .background(Indigo.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "🎯 Cấu hình đang ghép: ${config.selectedCore.displayName} • Bản $displaySelectedVersion • $gamemodeLabelVi",
                            color = Indigo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            buildString {
                                append(if (flexibleCrackedMode) "🔓 Cho phép Cracked/Offline" else "🔐 Chỉ tài khoản bản quyền")
                                if (config.selectedCore.isBedrock) {
                                    append(" • 📱 Thuần Bedrock PE (UDP)")
                                } else if (flexibleCrossplayEnabled && config.selectedCore.supportsPlugins) {
                                    append(
                                        when {
                                            crossplayCheck == null -> " • 📱💻 Chơi chung PC + PE: đang kiểm tra..."
                                            crossplayCheck.supported -> " • 📱💻 Chơi chung: PC Java ${config.mcVersion} + PE ${crossplayCheck.bedrockRangeText}"
                                            else -> " • ❌ Java ${config.mcVersion} này PE KHÔNG vào được — xem gợi ý bên dưới"
                                        }
                                    )
                                } else if (config.selectedCore.supportsMods) {
                                    append(" • 🧩 Hỗ trợ cài Mod .jar")
                                } else if (config.selectedCore.supportsPlugins) {
                                    append(" • ⚡ Hỗ trợ cài Plugin .jar")
                                }
                            },
                            color = Mint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(McShape.Row)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, Indigo.copy(alpha = 0.35f), McShape.Row)
                                .clickable { showVersionPickerModal = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "🎮 Phiên bản đang chọn: $displaySelectedVersion",
                                    color = Indigo,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Bấm vào đây để lướt chọn hoặc nhập tay phiên bản rồi ấn cài luôn",
                                    color = Muted,
                                    fontSize = 10.sp
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "📋 Lướt chọn ▾",
                                    color = Indigo,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Indigo.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                                Text(
                                    if (isDownloadingCore) "⏳..." else "⬇️ Cài luôn",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDownloadingCore) Muted else Mint)
                                        .clickable(enabled = !isDownloadingCore) {
                                            triggerQuickInstallForVersion(
                                                targetCore = config.selectedCore,
                                                targetVerKey = config.mcVersion,
                                                peLabel = selectedPeHint
                                            )
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "1️⃣ Chọn nhanh định hướng Server (Giữ nguyên phiên bản ${config.mcVersion} hoặc đổi tùy ý ở mục Chọn Phiên Bản bên dưới):",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SegPill(
                            text = "🧩 Chơi Mod (Fabric)",
                            selected = config.selectedCore == ServerCore.Fabric,
                            compact = true,
                            onClick = {
                                flexibleCrossplayEnabled = false
                                vm.selectCore(ServerCore.Fabric)
                            }
                        )
                        SegPill(
                            text = "🌱 Sinh tồn & Plugin (Paper)",
                            selected = config.selectedCore == ServerCore.Paper && !flexibleCrossplayEnabled,
                            compact = true,
                            onClick = {
                                flexibleCrossplayEnabled = false
                                vm.selectCore(ServerCore.Paper)
                            }
                        )
                        SegPill(
                            text = "📱💻 Chơi Chung PC + PE",
                            selected = flexibleCrossplayEnabled && config.selectedCore.supportsPlugins,
                            compact = true,
                            onClick = {
                                if (!config.selectedCore.supportsPlugins) {
                                    vm.selectCore(ServerCore.Paper)
                                }
                                flexibleCrossplayEnabled = true
                                flexibleCrackedMode = true
                            }
                        )
                        SegPill(
                            text = "⚒️ Modpack (NeoForge)",
                            selected = config.selectedCore == ServerCore.NeoForge,
                            compact = true,
                            onClick = {
                                flexibleCrossplayEnabled = false
                                vm.selectCore(ServerCore.NeoForge)
                            }
                        )
                        SegPill(
                            text = "🛡️ Mod Cổ Điển (Forge)",
                            selected = config.selectedCore == ServerCore.Forge,
                            compact = true,
                            onClick = {
                                flexibleCrossplayEnabled = false
                                vm.selectCore(ServerCore.Forge)
                            }
                        )
                        SegPill(
                            text = "🔮 Purpur (Mở rộng)",
                            selected = config.selectedCore == ServerCore.Purpur,
                            compact = true,
                            onClick = {
                                vm.selectCore(ServerCore.Purpur)
                            }
                        )
                        SegPill(
                            text = "📱 Thuần Bedrock PE",
                            selected = config.selectedCore == ServerCore.PowerNukkitX,
                            compact = true,
                            onClick = {
                                flexibleCrossplayEnabled = false
                                vm.selectCore(ServerCore.PowerNukkitX)
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "2️⃣ Chế độ chơi khởi tạo (Tự động thiết lập khi tạo Server):",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val gamemodes = listOf(
                            "survival" to "🌱 Sinh tồn",
                            "creative" to "🎨 Sáng tạo",
                            "adventure" to "🗺️ Phiêu lưu",
                            "hardcore" to "💀 Siêu khó (Hardcore)"
                        )
                        gamemodes.forEach { (modeKey, modeLabel) ->
                            SegPill(
                                text = modeLabel,
                                selected = flexibleGamemode == modeKey,
                                compact = true,
                                onClick = { flexibleGamemode = modeKey }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "3️⃣ Tùy chọn Tài khoản & Cầu nối Đa nền tảng (Linh hoạt theo nhu cầu):",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(McShape.Row)
                            .background(com.mineserve.mobile.ui.theme.FieldGray)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    "🔓 Cho phép tài khoản Cracked / Offline",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Tự bật online-mode=false để TLauncher, PojavLauncher, FCL và PE vào được ngay",
                                    color = Muted,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Switch(
                                checked = flexibleCrackedMode,
                                onCheckedChange = { flexibleCrackedMode = it }
                            )
                        }

                        if (config.selectedCore.supportsPlugins) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        "📱💻 Tích hợp Chơi Chung PC + Điện thoại PE",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (flexibleCrossplayEnabled) Mint else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Tự động cài GeyserMC + Floodgate + ViaVersion + ViaBackwards cho ${config.selectedCore.displayName} ${config.mcVersion}",
                                        color = Muted,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                                Switch(
                                    checked = flexibleCrossplayEnabled,
                                    onCheckedChange = { enabled ->
                                        flexibleCrossplayEnabled = enabled
                                        if (enabled) flexibleCrackedMode = true
                                    }
                                )
                            }

                            if (crossplayWanted) {
                                Spacer(Modifier.height(4.dp))
                                com.mineserve.mobile.ui.PeVersionPicker(
                                    selected = flexibleBedrockBranch,
                                    onSelect = { flexibleBedrockBranch = it }
                                )
                                Spacer(Modifier.height(4.dp))
                                com.mineserve.mobile.ui.CrossplayCompatCard(
                                    core = config.selectedCore,
                                    mcVersion = config.mcVersion,
                                    result = crossplayCheck,
                                    onApplySuggestion = { sugCore, sugVer -> vm.selectCoreAndVersion(sugCore, sugVer) },
                                    onRetry = { crossplayRefreshKey++ }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(McShape.Row)
                            .background(com.mineserve.mobile.ui.theme.FieldGray)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "💾 Bộ nhớ trống hiện tại: ${freeStorageMb} MB • RAM cấp phát: ${config.maxHeapMb} MB",
                            color = if (freeStorageMb > 1024) Indigo else Coral,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (showSoftwareComparison) "▲ Bấm để ẩn bảng so sánh Core" else "▼ Bấm xem so sánh Paper / Fabric / Forge / PE",
                            color = Mint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { showSoftwareComparison = !showSoftwareComparison }
                        )
                    }

                    if (showSoftwareComparison) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(McShape.Row)
                                .background(IndigoSoft)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("📊 Bảng So Sánh Phần Mềm Server (Nên chọn loại nào?):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Indigo)
                            Text("• 🧩 Fabric (Khuyên dùng cho Mod): Siêu nhẹ, khởi động nhanh, hỗ trợ mọi phiên bản từ 1.14 → 1.21.11 / 26.x.", fontSize = 11.sp, color = Muted)
                            Text("• ⚡ Paper / Purpur (Khuyên dùng cho Sinh tồn, Plugin & Chơi chung PC+PE): Tối ưu TPS cực tốt trên điện thoại, ghép được với mọi bản qua ViaVersion + GeyserMC.", fontSize = 11.sp, color = Muted)
                            Text("• ⚒️ Forge / NeoForge: Chuyên trị các gói Modpack lớn (1.7.10 → 1.21.x); khuyên dùng RAM ≥ 4GB.", fontSize = 11.sp, color = Muted)
                            Text("• 🧱 Vanilla: Bản gốc Mojang 100%, không cài được Mod hay Plugin.", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }

            item(key = "installed_cores") {
                McCard(
                    title = "✅ Máy chủ / Core đã tải & cài đặt (${config.installedCores.size})",
                    compact = true,
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { installedExpanded = !installedExpanded }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                stringResource(
                                    if (installedExpanded) R.string.dl_installed_collapse
                                    else R.string.dl_installed_expand
                                ),
                                color = Muted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                if (installedExpanded) Icons.Outlined.KeyboardArrowUp
                                else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                ) {
                    val installed = config.installedCores
                    if (installedExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (installed.isEmpty()) {
                                Text(
                                    stringResource(R.string.s452),
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                            } else {
                                installed.forEach { core ->
                                    val isSelectedCore = core.name == config.activeCoreName
                                    val srvSizeMb = (storageMap[core.dirName]?.totalBytes ?: 0L) / (1024L * 1024L)
                                    val versionText = "🧩 ${core.core.displayName} ${core.version}" +
                                        (if (core.core == ServerCore.PowerNukkitX) {
                                            " (" + stringResource(R.string.dl_supported_game, versionHints[core.version] ?: stringResource(R.string.dl_official_unknown)) + ")"
                                        } else "") + " • 💾 ${srvSizeMb} MB\n📂 " + stringResource(R.string.dl_folder, core.dirName)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(McShape.Row)
                                            .background(if (isSelectedCore) Mint.copy(alpha = 0.12f) else com.mineserve.mobile.ui.theme.FieldGray)
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            com.mineserve.mobile.ui.CoreBrandLogo(core = core.core, size = 36.dp)
                                            Spacer(Modifier.size(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        core.name,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    if (isSelectedCore) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(McShape.Small)
                                                                .background(Mint.copy(alpha = 0.22f))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                "✅ Đang chọn",
                                                                color = Mint,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    versionText,
                                                    color = Muted,
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    vm.setActiveCore(core.name)
                                                    scope.launch { snackbarHostState.showInstantSnackbar("✅ Đã chọn máy chủ: ${core.name}") }
                                                },
                                                enabled = !isSelectedCore,
                                                shape = McShape.ControlCompact,
                                                border = BorderStroke(1.dp, if (isSelectedCore) Mint else Indigo),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    if (isSelectedCore) "✅ Đang dùng" else "🔄 Chọn SV",
                                                    color = if (isSelectedCore) Mint else Indigo,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { vm.verifyOrRepairCore(core.dirName) },
                                                shape = McShape.ControlCompact,
                                                border = BorderStroke(1.dp, Mint),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    stringResource(R.string.dl_core_verify),
                                                    color = Mint,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { pendingDeleteCore = core },
                                                enabled = !serverState.isRunning,
                                                shape = McShape.ControlCompact,
                                                border = BorderStroke(1.dp, Coral),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.weight(0.75f)
                                            ) {
                                                Text(
                                                    stringResource(R.string.s339),
                                                    color = Coral,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (serverState.isRunning) {
                                Text(stringResource(R.string.dl_core_delete_running), color = Coral, fontSize = 10.sp)
                            }
                            Text(
                                stringResource(R.string.s456),
                                color = Muted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            item(key = "select_core") {
                McCard(
                    title = stringResource(R.string.s457),
                    compact = true,
                    trailing = {
                        Text(
                            "📥 Nhập server",
                            color = Indigo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { showImportDialog = true }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                ) {
                    Text(
                        "🖼️ Chọn phần mềm Server (Core) — linh hoạt ghép với bất kỳ phiên bản Minecraft nào ở bước tiếp theo:",
                        color = Muted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    val featuredCores = remember {
                        listOf(
                            Triple(ServerCore.Fabric, "Fabric (Chơi Mod Nhẹ Mượt)", "Khởi động nhanh • Hỗ trợ mọi bản Mod từ 1.14 → 1.21.11 / 26.x"),
                            Triple(ServerCore.Paper, "Paper (Sinh Tồn & Plugin)", "Siêu mượt 20 TPS • Hỗ trợ Plugin & GeyserMC chơi chung PC + PE"),
                            Triple(ServerCore.NeoForge, "NeoForge (Modpack Hiện Đại)", "Chạy các gói Modpack lớn 1.20.4–1.21+ (Cần RAM ≥ 4GB)"),
                            Triple(ServerCore.Forge, "Forge (Modpack Cổ Điển)", "Hỗ trợ các Modpack lớn kinh điển 1.7.10 / 1.12.2 / 1.16.5 / 1.20.1"),
                            Triple(ServerCore.Purpur, "Purpur (Sinh Tồn Mở Rộng)", "Nhánh nâng cấp của Paper với hàng trăm tùy chỉnh thú vị"),
                            Triple(ServerCore.PocketMine, "PocketMine-MP (Minecraft PE 1.19.80–1.21.101)", "Siêu nhẹ (PHP 8.2) • Đầy đủ các bản PE 1.19.80 → 1.20.81 → 1.21.101 (82 bản PE)"),
                            Triple(ServerCore.Allay, "Allay (Minecraft PE 1.21.50–1.21.132 & 1.26.x)", "Nhân Bedrock hiện đại (Java 21) • Đầy đủ các bản PE 1.21.50 → 1.21.132 & 1.26.x (50 bản PE)"),
                            Triple(ServerCore.PowerNukkitX, "PowerNukkitX (Minecraft PE 1.19.10–1.20.51 & 1.26.x)", "Có Mob AI & Thế giới Vanilla • Đầy đủ PE 1.19.10 → 1.20.51 & 1.26.0–1.26.50 (53 bản PE)")
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        featuredCores.forEach { (core, titleLabel, subtitleLabel) ->
                            val isSelected = config.selectedCore == core
                            val installedCount = installedCoreCounts[core] ?: 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(McShape.Row)
                                    .background(
                                        if (isSelected) Indigo.copy(alpha = 0.22f)
                                        else com.mineserve.mobile.ui.theme.FieldGray
                                    )
                                    .clickable {
                                        customCoreName = ""
                                        vm.selectCore(core)
                                        scope.launch {
                                            snackbarHostState.showInstantSnackbar("✅ Đã chọn phần mềm Core: ${core.displayName}")
                                        }
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.mineserve.mobile.ui.CoreBrandLogo(core = core, size = 40.dp)
                                Spacer(Modifier.size(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            titleLabel,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Indigo else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (installedCount > 0) {
                                            Text(
                                                "✅ Đã cài ($installedCount)",
                                                color = Mint,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        subtitleLabel,
                                        color = Muted,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { showMoreCores = !showMoreCores },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (showMoreCores) "▲ Thu gọn các loại Core khác"
                            else "▼ Hiện thêm 10 loại Core khác (Leaves, Leaf, Spigot, Quilt, Vanilla, PocketMine...)",
                            color = Indigo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (showMoreCores) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            ServerCore.values().filter { it != ServerCore.Unknown }.forEach { core ->
                                val countForCore = installedCoreCounts[core] ?: 0
                                val isSelCore = config.selectedCore == core
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(McShape.Row)
                                        .background(
                                            when {
                                                isSelCore -> Indigo
                                                countForCore > 0 -> Mint.copy(alpha = 0.16f)
                                                core.isBedrock -> Coral.copy(alpha = 0.16f)
                                                else -> com.mineserve.mobile.ui.theme.FieldGray
                                            }
                                        )
                                        .clickable {
                                            customCoreName = ""
                                            vm.selectCore(core)
                                            scope.launch {
                                                snackbarHostState.showInstantSnackbar("✅ Đã chọn phần mềm Core: ${core.displayName}")
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    com.mineserve.mobile.ui.CoreBrandLogo(core = core, size = 22.dp)
                                    Text(
                                        text = if (countForCore > 0) "${core.displayName} ($countForCore)" else core.displayName,
                                        color = if (isSelCore) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelCore) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (config.selectedCore) {
                            ServerCore.Paper -> stringResource(R.string.dl_desc_paper)
                            ServerCore.Purpur -> stringResource(R.string.dl_desc_purpur)
                            ServerCore.Leaves -> stringResource(R.string.dl_desc_leaves)
                            ServerCore.Leaf -> stringResource(R.string.dl_desc_leaf)
                            ServerCore.Spigot -> stringResource(R.string.dl_desc_spigot)
                            ServerCore.CraftBukkit -> stringResource(R.string.dl_desc_craftbukkit)
                            ServerCore.Fabric -> stringResource(R.string.dl_desc_fabric)
                            ServerCore.Forge -> stringResource(R.string.dl_desc_forge)
                            ServerCore.NeoForge -> stringResource(R.string.dl_desc_neoforge)
                            ServerCore.Quilt -> stringResource(R.string.dl_desc_quilt)
                            ServerCore.Vanilla -> stringResource(R.string.dl_desc_vanilla)
                            ServerCore.Velocity -> stringResource(R.string.dl_desc_velocity)
                            ServerCore.BungeeCord -> stringResource(R.string.dl_desc_bungeecord)
                            ServerCore.PowerNukkitX -> stringResource(R.string.dl_desc_pnx)
                            ServerCore.PocketMine -> stringResource(R.string.dl_desc_pocketmine)
                            ServerCore.Allay -> stringResource(R.string.dl_desc_allay)
                            ServerCore.Unknown -> stringResource(R.string.dl_desc_unknown)
                        },
                        color = Muted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            if (config.selectedCore.supportsPlugins) stringResource(R.string.dl_supports_plugins) else stringResource(R.string.dl_no_plugins),
                            color = if (config.selectedCore.supportsPlugins) Mint else Coral,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (config.selectedCore.supportsMods) stringResource(R.string.dl_supports_mods) else stringResource(R.string.dl_no_mods),
                            color = if (config.selectedCore.supportsMods) Mint else Coral,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = McShape.ControlCompact
                    ) { Text("Nhập server có sẵn (ZIP / JAR / Thư mục)", color = Indigo, fontSize = 12.sp) }
                }
            }

            item(key = "select_version") {
                McCard(title = if (usesCoreVersion) stringResource(R.string.dl_select_version) else stringResource(R.string.s471), compact = true) {
                    // 1. Clickable Current Version Selector Box (tap to open full modal or install immediately)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(McShape.Row)
                            .background(Indigo.copy(alpha = 0.09f))
                            .border(1.dp, Indigo.copy(alpha = 0.35f), McShape.Row)
                            .clickable { showVersionPickerModal = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (usesCoreVersion) "📱 Đang chọn: $displaySelectedVersion" else "☕ Đang chọn: Minecraft ${config.mcVersion}",
                                color = Indigo,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Ấn vào khung này để mở bảng lướt chọn hoặc nhập tay bên dưới rồi ấn Cài luôn",
                                color = Muted,
                                fontSize = 10.5.sp
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📋 Lướt chọn ▾",
                                color = Indigo,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Indigo.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            Text(
                                if (isDownloadingCore) "⏳..." else "⬇️ Cài luôn",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDownloadingCore) Muted else Mint)
                                    .clickable(enabled = !isDownloadingCore) {
                                        triggerQuickInstallForVersion(
                                            targetCore = config.selectedCore,
                                            targetVerKey = config.mcVersion,
                                            peLabel = selectedPeHint
                                        )
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 2. Live Search / Manual Version Input Box
                    val trimmedSearch = customVersion.trim()
                    val customLooksValid = trimmedSearch.isNotBlank() &&
                        (Regex("^\\d+(\\.\\d+)+.*$").matches(trimmedSearch) || trimmedSearch in availableVersions)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customVersion,
                            onValueChange = { customVersion = it },
                            placeholder = {
                                Text(
                                    if (usesCoreVersion) "🔍 Nhập tìm hoặc gõ tay bản PE (VD: 1.21.132, 1.21.50, 1.20.81...)"
                                    else "🔍 Nhập tìm hoặc gõ tay phiên bản (VD: 1.21.4, 1.20.1, 1.16.5...)",
                                    fontSize = 11.5.sp
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            trailingIcon = {
                                if (customVersion.isNotEmpty()) {
                                    Text(
                                        "✕",
                                        color = Muted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { customVersion = "" }
                                            .padding(8.dp)
                                    )
                                }
                            }
                        )
                    }

                    // Instant Custom Version Action Row when user types a version
                    if (trimmedSearch.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        val matchedBedrock = if (usesCoreVersion) {
                            unifiedBedrockEntries.firstOrNull {
                                it.peVersion.equals(trimmedSearch, ignoreCase = true) ||
                                    it.coreVersionKey.substringBefore("@").equals(trimmedSearch, ignoreCase = true)
                            }
                        } else null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(McShape.Row)
                                .background(Mint.copy(alpha = 0.12f))
                                .border(1.dp, Mint.copy(alpha = 0.38f), McShape.Row)
                                .clickable {
                                    if (!customLooksValid && matchedBedrock == null) {
                                        scope.launch {
                                            snackbarHostState.showInstantSnackbar("⚠️ Phiên bản '$trimmedSearch' chưa đúng định dạng (VD: 1.21.50, 1.20.1)")
                                        }
                                    } else if (matchedBedrock != null) {
                                        if (matchedBedrock.core == config.selectedCore) {
                                            vm.setMcVersion(matchedBedrock.coreVersionKey)
                                        } else {
                                            vm.selectCoreAndVersion(matchedBedrock.core, matchedBedrock.coreVersionKey)
                                        }
                                        scope.launch {
                                            snackbarHostState.showInstantSnackbar("✅ Đã chọn PE ${matchedBedrock.peVersion}")
                                        }
                                    } else {
                                        vm.setMcVersion(trimmedSearch)
                                        scope.launch {
                                            snackbarHostState.showInstantSnackbar("✅ Đã chọn phiên bản: $trimmedSearch")
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (matchedBedrock != null) "🎯 Khớp chính xác: PE ${matchedBedrock.peVersion} (${matchedBedrock.core.displayName})"
                                    else "✨ Phiên bản nhập tay: $trimmedSearch",
                                    color = Mint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Ấn để chọn hoặc bấm nút bên phải để tải & cài đặt ngay",
                                    color = Muted,
                                    fontSize = 10.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "✓ Chọn",
                                    color = Indigo,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Indigo.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                                Text(
                                    if (isDownloadingCore) "⏳..." else "⬇️ Cài luôn",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDownloadingCore) Muted else Mint)
                                        .clickable(enabled = !isDownloadingCore) {
                                            if (!customLooksValid && matchedBedrock == null) {
                                                scope.launch {
                                                    snackbarHostState.showInstantSnackbar("⚠️ Phiên bản '$trimmedSearch' không hợp lệ! Ví dụ: 1.21.50, 1.20.1")
                                                }
                                            } else if (matchedBedrock != null) {
                                                triggerQuickInstallForVersion(
                                                    targetCore = matchedBedrock.core,
                                                    targetVerKey = matchedBedrock.coreVersionKey,
                                                    peLabel = matchedBedrock.peVersion
                                                )
                                            } else {
                                                triggerQuickInstallForVersion(
                                                    targetCore = config.selectedCore,
                                                    targetVerKey = trimmedSearch,
                                                    peLabel = if (usesCoreVersion) trimmedSearch else null
                                                )
                                            }
                                        }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (isLoadingVersions) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Indigo,
                                strokeWidth = 2.dp
                            )
                            Text(stringResource(R.string.s472), color = Muted, fontSize = 12.sp)
                        }
                    } else if (availableVersions.isEmpty()) {
                        Text(stringResource(R.string.s473), color = Muted, fontSize = 12.sp)
                    } else {
                        val branchChips = if (usesCoreVersion) {
                            val countNative = unifiedBedrockEntries.count { it.isNativeToSelected }
                            val count121 = unifiedBedrockEntries.count { it.peVersion == "1.21" || it.peVersion.startsWith("1.21.") }
                            val count120 = unifiedBedrockEntries.count { it.peVersion == "1.20" || it.peVersion.startsWith("1.20.") }
                            val count119 = unifiedBedrockEntries.count { it.peVersion.startsWith("1.19") }
                            val count126 = unifiedBedrockEntries.count { it.peVersion.startsWith("1.26.") || it.peVersion.startsWith("26.") }
                            listOf(
                                "all" to "Tất cả (${unifiedBedrockEntries.size})",
                                "only_selected" to "🔹 Riêng ${config.selectedCore.displayName} ($countNative)",
                                "1.21" to "🌟 PE 1.21.x ($count121)",
                                "1.20" to "⚡ PE 1.20.x ($count120)",
                                "1.16_19" to "🛡️ PE 1.19.x ($count119)",
                                "26" to "🚀 PE 1.26 / 26.x ($count126)"
                            )
                        } else {
                            listOf(
                                "all" to "Tất cả (${availableVersions.size})",
                                "1.21" to "🌟 1.21.x",
                                "1.20" to "⚡ 1.20.x",
                                "1.16_19" to "🛡️ 1.16–1.19",
                                "legacy" to "🏛️ 1.7–1.15",
                                "26" to "🚀 26.x"
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            branchChips.forEach { (key, label) ->
                                SegPill(
                                    text = label,
                                    selected = versionBranchFilter == key,
                                    compact = true,
                                    onClick = { versionBranchFilter = key }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (usesCoreVersion) {
                            val filteredBedrockEntries = remember(unifiedBedrockEntries, versionBranchFilter, trimmedSearch) {
                                val byBranch = when (versionBranchFilter) {
                                    "all" -> unifiedBedrockEntries
                                    "only_selected" -> unifiedBedrockEntries.filter { it.isNativeToSelected }
                                    "1.21" -> unifiedBedrockEntries.filter { it.peVersion == "1.21" || it.peVersion.startsWith("1.21.") }
                                    "26" -> unifiedBedrockEntries.filter { it.peVersion.startsWith("26.") || it.peVersion.startsWith("1.26.") }
                                    "1.20" -> unifiedBedrockEntries.filter { it.peVersion == "1.20" || it.peVersion.startsWith("1.20.") }
                                    "1.16_19" -> unifiedBedrockEntries.filter {
                                        it.peVersion.startsWith("1.19") || it.peVersion.startsWith("1.18") ||
                                            it.peVersion.startsWith("1.17") || it.peVersion.startsWith("1.16")
                                    }
                                    else -> unifiedBedrockEntries
                                }
                                if (trimmedSearch.isBlank()) {
                                    byBranch
                                } else {
                                    byBranch.filter {
                                        it.peVersion.contains(trimmedSearch, ignoreCase = true) ||
                                            it.coreVersionKey.contains(trimmedSearch, ignoreCase = true) ||
                                            it.core.displayName.contains(trimmedSearch, ignoreCase = true)
                                    }
                                }
                            }
                            Text(
                                "📋 Danh sách lướt chọn (${filteredBedrockEntries.size} phiên bản Bedrock / PE) — chạm để chọn hoặc bấm「⬇️ Cài luôn」:",
                                color = Muted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 210.dp, max = 330.dp)
                                    .clip(McShape.Row)
                                    .background(com.mineserve.mobile.ui.theme.FieldGray.copy(alpha = 0.55f))
                                    .border(1.dp, Indigo.copy(alpha = 0.16f), McShape.Row)
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                items(
                                    items = filteredBedrockEntries,
                                    key = { "${it.core.name}_${it.peVersion}_${it.coreVersionKey}" }
                                ) { entry ->
                                    val cleanCoreVer = entry.coreVersionKey.substringBefore("@")
                                    val verInstalled = (entry.core to cleanCoreVer) in installedBedrockCoreVersions
                                    val isVerSelected = (entry.core == config.selectedCore) &&
                                        (config.mcVersion == entry.coreVersionKey || selectedPeHint == entry.peVersion)
                                    val coreShort = when (entry.core) {
                                        ServerCore.PocketMine -> "PocketMine-MP"
                                        ServerCore.PowerNukkitX -> "PowerNukkitX"
                                        ServerCore.Allay -> "Allay"
                                        else -> entry.core.displayName
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isVerSelected -> Indigo.copy(alpha = 0.20f)
                                                    verInstalled -> Mint.copy(alpha = 0.12f)
                                                    else -> MaterialTheme.colorScheme.surface
                                                }
                                            )
                                            .clickable {
                                                if (entry.core == config.selectedCore) {
                                                    vm.setMcVersion(entry.coreVersionKey)
                                                    scope.launch {
                                                        snackbarHostState.showInstantSnackbar("✅ Đã chọn Minecraft PE ${entry.peVersion} ($coreShort $cleanCoreVer)")
                                                    }
                                                } else {
                                                    vm.selectCoreAndVersion(entry.core, entry.coreVersionKey)
                                                    scope.launch {
                                                        snackbarHostState.showInstantSnackbar("✅ Đã chọn PE ${entry.peVersion} (Tự chuyển sang ${entry.core.displayName} $cleanCoreVer)")
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                if (verInstalled) "✅ Minecraft PE ${entry.peVersion} (Đã tải)"
                                                else "📱 Minecraft PE ${entry.peVersion}",
                                                color = if (isVerSelected) Indigo else if (verInstalled) Mint else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                if (entry.isNativeToSelected) "📦 $coreShort • Core $cleanCoreVer"
                                                else "🔄 Tự chuyển sang $coreShort • Core $cleanCoreVer",
                                                color = if (isVerSelected) Indigo else if (entry.isNativeToSelected) Muted else Coral,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isVerSelected) {
                                                Text(
                                                    "Đang chọn",
                                                    color = Indigo,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                when {
                                                    isDownloadingCore -> "⏳..."
                                                    verInstalled -> "➕ Cài thêm"
                                                    else -> "⬇️ Cài luôn"
                                                },
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        when {
                                                            isDownloadingCore -> Muted
                                                            verInstalled -> Indigo
                                                            else -> Mint
                                                        }
                                                    )
                                                    .clickable(enabled = !isDownloadingCore) {
                                                        triggerQuickInstallForVersion(
                                                            targetCore = entry.core,
                                                            targetVerKey = entry.coreVersionKey,
                                                            peLabel = entry.peVersion
                                                        )
                                                    }
                                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val filteredVersions = remember(availableVersions, versionHints, versionBranchFilter, trimmedSearch) {
                                val byBranch = if (versionBranchFilter == "all") {
                                    availableVersions
                                } else {
                                    availableVersions.filter { v ->
                                        when (versionBranchFilter) {
                                            "1.21" -> v == "1.21" || v.startsWith("1.21.")
                                            "26" -> v.startsWith("26.") || v.startsWith("1.26.")
                                            "1.20" -> v == "1.20" || v.startsWith("1.20.")
                                            "1.16_19" -> v.startsWith("1.19") || v.startsWith("1.18") || v.startsWith("1.17") || v.startsWith("1.16")
                                            "legacy" -> v.startsWith("1.15") || v.startsWith("1.14") || v.startsWith("1.13") ||
                                                v.startsWith("1.12") || v.startsWith("1.11") || v.startsWith("1.10") ||
                                                v.startsWith("1.9") || v.startsWith("1.8") || v.startsWith("1.7")
                                            else -> true
                                        }
                                    }
                                }
                                if (trimmedSearch.isBlank()) {
                                    byBranch
                                } else {
                                    byBranch.filter { it.contains(trimmedSearch, ignoreCase = true) }
                                }
                            }
                            if (filteredVersions.isEmpty()) {
                                Text(
                                    "Không có sẵn trong danh sách — bạn có thể bấm「⬇️ Cài luôn」ở dòng phiên bản nhập tay phía trên.",
                                    color = Coral,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    "📋 Danh sách lướt chọn (${filteredVersions.size} phiên bản ${config.selectedCore.displayName}) — chạm để chọn hoặc bấm「⬇️ Cài luôn」:",
                                    color = Muted,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 210.dp, max = 330.dp)
                                        .clip(McShape.Row)
                                        .background(com.mineserve.mobile.ui.theme.FieldGray.copy(alpha = 0.55f))
                                        .border(1.dp, Indigo.copy(alpha = 0.16f), McShape.Row)
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    items(
                                        items = filteredVersions,
                                        key = { it }
                                    ) { ver ->
                                        val verInstalled = ver in installedVersionsForSelectedCore
                                        val isVerSelected = config.mcVersion == ver
                                        val verTag = when {
                                            ver.startsWith("26.") -> "🚀 Thế hệ 26 • Java 25/21"
                                            ver.startsWith("1.21.11") || ver.startsWith("1.21.4") -> "🌟 Mới nhất • Java 21"
                                            ver.startsWith("1.21") -> "🛡️ Ổn định • Java 21"
                                            ver.startsWith("1.20.5") || ver.startsWith("1.20.6") -> "⚡ Nhanh • Java 21"
                                            ver.startsWith("1.20.1") -> "🧩 Nhiều Mod • Java 17"
                                            ver.startsWith("1.20") -> "⚡ Mượt • Java 17/21"
                                            ver.startsWith("1.17") || ver.startsWith("1.18") || ver.startsWith("1.19") -> "☕ Java 17"
                                            ver.startsWith("1.16.5") -> "🪶 Nhẹ máy • Java 17/11/8"
                                            ver.startsWith("1.12.2") || ver.startsWith("1.8") || ver.startsWith("1.7") -> "🏛️ Cổ điển • Java 8/11"
                                            else -> "☕ Minecraft $ver"
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        isVerSelected -> Indigo.copy(alpha = 0.20f)
                                                        verInstalled -> Mint.copy(alpha = 0.12f)
                                                        else -> MaterialTheme.colorScheme.surface
                                                    }
                                                )
                                                .clickable {
                                                    vm.setMcVersion(ver)
                                                    scope.launch {
                                                        snackbarHostState.showInstantSnackbar("✅ Đã chọn phiên bản Minecraft $ver")
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    if (verInstalled) "✅ Minecraft $ver (Đã tải)" else "📦 Minecraft $ver",
                                                    color = if (isVerSelected) Indigo else if (verInstalled) Mint else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    "${config.selectedCore.displayName} • $verTag",
                                                    color = if (isVerSelected) Indigo else Muted,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isVerSelected) {
                                                    Text(
                                                        "Đang chọn",
                                                        color = Indigo,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    when {
                                                        isDownloadingCore -> "⏳..."
                                                        verInstalled -> "➕ Cài thêm"
                                                        else -> "⬇️ Cài luôn"
                                                    },
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            when {
                                                                isDownloadingCore -> Muted
                                                                verInstalled -> Indigo
                                                                else -> Mint
                                                            }
                                                        )
                                                        .clickable(enabled = !isDownloadingCore) {
                                                            triggerQuickInstallForVersion(
                                                                targetCore = config.selectedCore,
                                                                targetVerKey = ver,
                                                                peLabel = null
                                                            )
                                                        }
                                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (usesCoreVersion) stringResource(R.string.dl_current_version, cleanSelectedCoreVer) else stringResource(R.string.s478, config.mcVersion),
                        color = Indigo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (usesCoreVersion) {
                        Text(
                            stringResource(R.string.dl_supported_game_colon, selectedPeHint ?: stringResource(R.string.dl_unknown_official)),
                            color = if (selectedPeHint == null) Muted else Coral,
                            fontSize = 10.sp
                        )
                    }
                    InstallerJavaNotice(
                        vm = vm,
                        core = config.selectedCore,
                        minecraftVersion = cleanSelectedCoreVer,
                        installedJava = installedJava,
                        selectedJavaRuntime = config.selectedJavaRuntime,
                        javaOperation = javaOperation,
                        isServerRunning = serverState.isRunning
                    )
                    if (usesCoreVersion) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.dl_pnx_protocol_hint),
                            color = Coral, fontSize = 11.sp
                        )
                    }
                }
            }

            item(key = "download_action") {
                McCard(title = stringResource(R.string.s479), compact = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDownloadingCore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Indigo,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "${downloadProgress.speedText} · ${downloadProgress.percent}%  ",
                                color = Indigo,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(McShape.Small)
                                    .background(Indigo.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("↓", color = Indigo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.s480),
                                color = Muted,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            stringResource(R.string.s403),
                            color = Indigo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onShowDownloadHelp() }
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    if (!isBootstrapped) {
                        Text(
                            stringResource(R.string.dl_env_init),
                            color = Coral,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            if (usesCoreVersion) {
                                stringResource(R.string.dl_preparing, config.selectedCore.displayName, displaySelectedVersion)
                            } else stringResource(R.string.s482, config.selectedCore.displayName, config.mcVersion),
                            color = Muted,
                            fontSize = 11.sp
                        )
                        if (config.selectedCore == ServerCore.PowerNukkitX) {
                            Text(
                                stringResource(R.string.dl_pnx_udp_hint),
                                color = Coral,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // Tóm tắt cấu hình linh hoạt sẽ được áp dụng khi tạo server
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(McShape.Row)
                                .background(com.mineserve.mobile.ui.theme.FieldGray)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "⚙️ Cấu hình tự động áp dụng: $gamemodeLabelVi • ${if (flexibleCrackedMode) "Cracked/Offline: BẬT" else "Chỉ Bản quyền"}",
                                color = Indigo,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (flexibleCrossplayEnabled && !config.selectedCore.isBedrock) {
                                Text(
                                    when {
                                        crossplayCheck == null -> "📱💻 Đang kiểm tra GeyserMC cho Java ${config.mcVersion}..."
                                        crossplayCheck.supported -> "📱💻 Tự cài GeyserMC + Floodgate + ViaVersion + ViaBackwards • PC: Java ${config.mcVersion} • PE: ${crossplayCheck.bedrockRangeText}"
                                        else -> "❌ Không tạo được server chơi chung với Java ${config.mcVersion}: ${crossplayCheck.suggestion ?: crossplayCheck.headline}"
                                    },
                                    color = if (crossplayCheck?.supported == false) Coral else Mint,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val defaultVersionSlug = if (usesCoreVersion && !selectedPeHint.isNullOrBlank()) {
                            "PE-$selectedPeHint"
                        } else {
                            cleanSelectedCoreVer
                        }
                        val defaultCoreName = when {
                            flexibleCrossplayEnabled && !config.selectedCore.isBedrock -> "Crossplay-${config.selectedCore.displayName}-$defaultVersionSlug"
                            flexibleGamemode == "creative" -> "SangTao-${config.selectedCore.displayName}-$defaultVersionSlug"
                            flexibleGamemode == "hardcore" -> "Hardcore-${config.selectedCore.displayName}-$defaultVersionSlug"
                            else -> "${config.selectedCore.displayName}-$defaultVersionSlug"
                        }
                        val effectiveCoreName = customCoreName.trim().ifBlank { defaultCoreName }
                        val existingSameCoreVer = config.installedCores.filter {
                            it.core == config.selectedCore && (it.version == config.mcVersion || it.version == cleanSelectedCoreVer)
                        }
                        val exactNameExists = config.installedCores.any {
                            it.name.equals(effectiveCoreName, ignoreCase = true)
                        }

                        if (existingSameCoreVer.isNotEmpty() && !wantsAdditionalServer) {
                            val firstExisting = existingSameCoreVer.first()
                            val existingSizeMb = (storageMap[firstExisting.dirName]?.totalBytes ?: 0L) / (1024L * 1024L)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(McShape.Row)
                                    .background(Mint.copy(alpha = 0.16f))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "✅ ĐÃ TẢI & CÀI ĐẶT SẴN SÀNG: ${firstExisting.name}",
                                    color = Mint,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Máy chủ ${config.selectedCore.displayName} $displaySelectedVersion (💾 ${existingSizeMb} MB) đã có sẵn trên máy!" +
                                        if (flexibleCrossplayEnabled && !config.selectedCore.isBedrock) "\n📱 Khi bấm nút bên dưới, ứng dụng sẽ tự động cài thêm GeyserMC + Floodgate + ViaVersion + ViaBackwards vào ${firstExisting.name} để cả PE và PC chơi chung!"
                                        else " Bạn có thể khởi động ngay mà không cần tải lại:",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Button(
                                    onClick = {
                                        vm.setActiveCore(firstExisting.name)
                                        if (flexibleCrossplayEnabled && !config.selectedCore.isBedrock) {
                                            vm.installCrossplayGeyserPack(flexibleBedrockBranch, startAfterInstall = false)
                                        }
                                        onBack?.invoke()
                                    },
                                    enabled = !crossplayBlocked,
                                    colors = ButtonDefaults.buttonColors(containerColor = Mint),
                                    shape = McShape.Control,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (flexibleCrossplayEnabled && !config.selectedCore.isBedrock) {
                                            "📱 CÀI GEYSER (PC+PE) & CHỌN SERVER「${firstExisting.name}」"
                                        } else {
                                            "🚀 VÀO MỤC KHỞI ĐỘNG SERVER「${firstExisting.name}」NGAY"
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                if (!serverState.isRunning) {
                                    Button(
                                        onClick = {
                                            vm.setActiveCore(firstExisting.name)
                                            if (flexibleCrossplayEnabled && !config.selectedCore.isBedrock) {
                                                vm.installCrossplayGeyserPack(flexibleBedrockBranch, startAfterInstall = true)
                                            } else {
                                                vm.startServer()
                                            }
                                            onBack?.invoke()
                                        },
                                        enabled = !crossplayBlocked,
                                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                                        shape = McShape.Control,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (flexibleCrossplayEnabled && !config.selectedCore.isBedrock) {
                                                "▶️ CÀI GEYSER (PC+PE) & BẬT「${firstExisting.name}」LUÔN"
                                            } else {
                                                "▶️ CHỌN & BẬT SERVER「${firstExisting.name}」LUÔN"
                                            },
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        wantsAdditionalServer = true
                                        customCoreName = "${defaultCoreName}-SV${existingSameCoreVer.size + 1}"
                                    },
                                    shape = McShape.ControlCompact,
                                    border = BorderStroke(1.dp, Indigo),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "➕ Tôi muốn tạo thêm 1 Server thứ ${existingSameCoreVer.size + 1} (Cùng phiên bản $displaySelectedVersion)",
                                        color = Indigo,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            if (existingSameCoreVer.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "➕ Đang tạo thêm Server mới chạy ${config.selectedCore.displayName} $displaySelectedVersion",
                                        color = Mint,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { wantsAdditionalServer = false; customCoreName = "" }) {
                                        Text("Dùng SV đã tải", color = Indigo, fontSize = 11.sp)
                                    }
                                }
                            }
                            Text(stringResource(R.string.s483), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = customCoreName,
                                onValueChange = { customCoreName = it },
                                placeholder = { Text("Mặc định: $defaultCoreName", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    vm.downloadCore(
                                        customName = effectiveCoreName,
                                        initialGamemode = flexibleGamemode,
                                        crackedOfflineMode = flexibleCrackedMode,
                                        installCrossplayPack = flexibleCrossplayEnabled && !config.selectedCore.isBedrock,
                                        crossplayBedrockBranch = flexibleBedrockBranch
                                    )
                                },
                                enabled = !isDownloadingCore && effectiveCoreName.isNotBlank() && !exactNameExists && !crossplayBlocked,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (exactNameExists) Mint else Indigo,
                                    disabledContainerColor = if (exactNameExists) Mint.copy(alpha = 0.16f) else IndigoSoft
                                ),
                                shape = McShape.Control,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isDownloadingCore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.size(8.dp))
                                }
                                Text(
                                    when {
                                        isDownloadingCore -> "⏳ Đang tải (${downloadProgress.percent}% • ${downloadProgress.speedText})..."
                                        exactNameExists -> "✅ Tên「$effectiveCoreName」đã tồn tại (Hãy nhập tên khác)"
                                        existingSameCoreVer.isNotEmpty() -> "➕ Tải & Tạo thêm máy chủ mới「$effectiveCoreName」"
                                        else -> "⬇️ Bắt đầu tải & Tạo Server「$effectiveCoreName」"
                                    },
                                    color = if (exactNameExists && !isDownloadingCore) Mint else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        val downloadLines = remember(consoleLines, isDownloadingCore) {
                            val filtered = consoleLines.filter { it.contains("[download]") || it.contains("[installer]") || it.contains("[Tải xuống]") }
                            if (filtered.isNotEmpty()) filtered else if (isDownloadingCore) consoleLines else emptyList()
                        }
                        if (isDownloadingCore || downloadLines.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.s487), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            downloadLines.takeLast(8).forEach { line ->
                                Text(
                                    com.mineserve.mobile.ui.TerminalLogTranslator.translate(line),
                                    color = Muted.copy(alpha = 0.85f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(16.dp))
            }
            }

            if (showVersionPickerModal) {
                var modalSearch by rememberSaveable { mutableStateOf("") }
                val trimmedModal = modalSearch.trim()
                val modalLooksValid = trimmedModal.isNotBlank() &&
                    (Regex("^\\d+(\\.\\d+)+.*$").matches(trimmedModal) || trimmedModal in availableVersions)
                AlertDialog(
                    onDismissRequest = { showVersionPickerModal = false },
                    title = {
                        Text(
                            if (usesCoreVersion) "📱 Lướt chọn & Cài nhanh Minecraft PE / Bedrock"
                            else "☕ Lướt chọn & Cài nhanh Minecraft (${config.selectedCore.displayName})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = modalSearch,
                                onValueChange = { modalSearch = it },
                                placeholder = {
                                    Text(
                                        if (usesCoreVersion) "🔍 Nhập tìm hoặc gõ tay (VD: 1.21.132, 1.21.50, 1.20.81...)"
                                        else "🔍 Nhập tìm hoặc gõ tay (VD: 1.21.4, 1.20.1, 1.16.5...)",
                                        fontSize = 11.5.sp
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    if (modalSearch.isNotEmpty()) {
                                        Text(
                                            "✕",
                                            color = Muted,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { modalSearch = "" }
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            )
                            if (trimmedModal.isNotBlank()) {
                                val matchedBedrock = if (usesCoreVersion) {
                                    unifiedBedrockEntries.firstOrNull {
                                        it.peVersion.equals(trimmedModal, ignoreCase = true) ||
                                            it.coreVersionKey.substringBefore("@").equals(trimmedModal, ignoreCase = true)
                                    }
                                } else null
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Mint.copy(alpha = 0.14f))
                                        .border(1.dp, Mint.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (matchedBedrock != null) {
                                                if (matchedBedrock.core == config.selectedCore) {
                                                    vm.setMcVersion(matchedBedrock.coreVersionKey)
                                                } else {
                                                    vm.selectCoreAndVersion(matchedBedrock.core, matchedBedrock.coreVersionKey)
                                                }
                                                showVersionPickerModal = false
                                            } else if (modalLooksValid) {
                                                vm.setMcVersion(trimmedModal)
                                                showVersionPickerModal = false
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (matchedBedrock != null) "🎯 PE ${matchedBedrock.peVersion} (${matchedBedrock.core.displayName})"
                                            else "✨ Phiên bản nhập tay: $trimmedModal",
                                            color = Mint,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            "Chạm để chọn hoặc bấm nút bên phải để tải & cài luôn",
                                            color = Muted,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "✓ Chọn",
                                            color = Indigo,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Indigo.copy(alpha = 0.14f))
                                                .padding(horizontal = 7.dp, vertical = 5.dp)
                                        )
                                        Text(
                                            if (isDownloadingCore) "⏳..." else "⬇️ Cài luôn",
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isDownloadingCore) Muted else Mint)
                                                .clickable(enabled = !isDownloadingCore) {
                                                    if (matchedBedrock != null) {
                                                        showVersionPickerModal = false
                                                        triggerQuickInstallForVersion(
                                                            targetCore = matchedBedrock.core,
                                                            targetVerKey = matchedBedrock.coreVersionKey,
                                                            peLabel = matchedBedrock.peVersion
                                                        )
                                                    } else if (modalLooksValid) {
                                                        showVersionPickerModal = false
                                                        triggerQuickInstallForVersion(
                                                            targetCore = config.selectedCore,
                                                            targetVerKey = trimmedModal,
                                                            peLabel = if (usesCoreVersion) trimmedModal else null
                                                        )
                                                    } else {
                                                        scope.launch {
                                                            snackbarHostState.showInstantSnackbar("⚠️ Phiên bản '$trimmedModal' không hợp lệ! Ví dụ: 1.21.50, 1.20.1")
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            if (usesCoreVersion) {
                                val modalBedrockList = remember(unifiedBedrockEntries, trimmedModal) {
                                    if (trimmedModal.isBlank()) unifiedBedrockEntries
                                    else unifiedBedrockEntries.filter {
                                        it.peVersion.contains(trimmedModal, ignoreCase = true) ||
                                            it.coreVersionKey.contains(trimmedModal, ignoreCase = true) ||
                                            it.core.displayName.contains(trimmedModal, ignoreCase = true)
                                    }
                                }
                                Text(
                                    "📋 Có ${modalBedrockList.size} phiên bản Bedrock / PE — lướt chọn hoặc bấm「⬇️ Cài luôn」:",
                                    color = Muted,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 240.dp, max = 380.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    items(
                                        items = modalBedrockList,
                                        key = { "modal_${it.core.name}_${it.peVersion}_${it.coreVersionKey}" }
                                    ) { entry ->
                                        val cleanCoreVer = entry.coreVersionKey.substringBefore("@")
                                        val verInstalled = (entry.core to cleanCoreVer) in installedBedrockCoreVersions
                                        val isVerSelected = (entry.core == config.selectedCore) &&
                                            (config.mcVersion == entry.coreVersionKey || selectedPeHint == entry.peVersion)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        isVerSelected -> Indigo.copy(alpha = 0.20f)
                                                        verInstalled -> Mint.copy(alpha = 0.12f)
                                                        else -> com.mineserve.mobile.ui.theme.FieldGray
                                                    }
                                                )
                                                .clickable {
                                                    if (entry.core == config.selectedCore) {
                                                        vm.setMcVersion(entry.coreVersionKey)
                                                    } else {
                                                        vm.selectCoreAndVersion(entry.core, entry.coreVersionKey)
                                                    }
                                                    showVersionPickerModal = false
                                                    scope.launch {
                                                        snackbarHostState.showInstantSnackbar("✅ Đã chọn Minecraft PE ${entry.peVersion} (${entry.core.displayName} $cleanCoreVer)")
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    if (verInstalled) "✅ PE ${entry.peVersion} (Đã tải)"
                                                    else "📱 PE ${entry.peVersion}",
                                                    color = if (isVerSelected) Indigo else if (verInstalled) Mint else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    "${entry.core.displayName} • Core $cleanCoreVer",
                                                    color = Muted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "✓ Chọn",
                                                    color = Indigo,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Indigo.copy(alpha = 0.14f))
                                                        .padding(horizontal = 7.dp, vertical = 5.dp)
                                                )
                                                Text(
                                                    when {
                                                        isDownloadingCore -> "⏳..."
                                                        verInstalled -> "➕ Cài thêm"
                                                        else -> "⬇️ Cài luôn"
                                                    },
                                                    color = Color.White,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isDownloadingCore) Muted else Mint)
                                                        .clickable(enabled = !isDownloadingCore) {
                                                            showVersionPickerModal = false
                                                            triggerQuickInstallForVersion(
                                                                targetCore = entry.core,
                                                                targetVerKey = entry.coreVersionKey,
                                                                peLabel = entry.peVersion
                                                            )
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val modalJavaList = remember(availableVersions, trimmedModal) {
                                    if (trimmedModal.isBlank()) availableVersions
                                    else availableVersions.filter { it.contains(trimmedModal, ignoreCase = true) }
                                }
                                Text(
                                    "📋 Có ${modalJavaList.size} phiên bản ${config.selectedCore.displayName} — lướt chọn hoặc bấm「⬇️ Cài luôn」:",
                                    color = Muted,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 240.dp, max = 380.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    items(
                                        items = modalJavaList,
                                        key = { "modal_java_$it" }
                                    ) { ver ->
                                        val verInstalled = ver in installedVersionsForSelectedCore
                                        val isVerSelected = config.mcVersion == ver
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        isVerSelected -> Indigo.copy(alpha = 0.20f)
                                                        verInstalled -> Mint.copy(alpha = 0.12f)
                                                        else -> com.mineserve.mobile.ui.theme.FieldGray
                                                    }
                                                )
                                                .clickable {
                                                    vm.setMcVersion(ver)
                                                    showVersionPickerModal = false
                                                    scope.launch {
                                                        snackbarHostState.showInstantSnackbar("✅ Đã chọn phiên bản Minecraft $ver")
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    if (verInstalled) "✅ Minecraft $ver (Đã tải)" else "📦 Minecraft $ver",
                                                    color = if (isVerSelected) Indigo else if (verInstalled) Mint else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    config.selectedCore.displayName,
                                                    color = Muted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "✓ Chọn",
                                                    color = Indigo,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Indigo.copy(alpha = 0.14f))
                                                        .padding(horizontal = 7.dp, vertical = 5.dp)
                                                )
                                                Text(
                                                    when {
                                                        isDownloadingCore -> "⏳..."
                                                        verInstalled -> "➕ Cài thêm"
                                                        else -> "⬇️ Cài luôn"
                                                    },
                                                    color = Color.White,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isDownloadingCore) Muted else Mint)
                                                        .clickable(enabled = !isDownloadingCore) {
                                                            showVersionPickerModal = false
                                                            triggerQuickInstallForVersion(
                                                                targetCore = config.selectedCore,
                                                                targetVerKey = ver,
                                                                peLabel = null
                                                            )
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showVersionPickerModal = false }) {
                            Text("Đóng", color = Indigo, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            pendingDeleteCore?.let { core ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteCore = null },
                    title = { Text(stringResource(R.string.dl_core_delete_title), fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            stringResource(R.string.dl_core_delete_content, core.name),
                            color = Muted, fontSize = 12.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingDeleteCore = null
                            vm.deleteCore(core.name)
                        }) { Text(stringResource(R.string.s339), color = Coral, fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteCore = null }) {
                            Text(stringResource(R.string.s402), color = Muted)
                        }
                    }
                )
            }
            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { if (!importingServer) { showImportDialog = false; pendingImport = null } },
                    title = { Text("Nhập server có sẵn") },
                    text = {
                        Column {
                            Text("Chọn nguồn nhập: Thư mục (sao chép toàn bộ thư mục server), Tệp nén (ZIP/TAR), Tệp JAR core, hoặc gói Modpack (.mrpack / ZIP có overrides).", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { folderImportLauncher.launch(null) }, enabled = !importingServer, shape = McShape.ControlCompact) { Text("Thư mục", fontSize = 11.sp) }
                                OutlinedButton(onClick = { archiveImportLauncher.launch(arrayOf("application/zip", "application/gzip", "application/x-tar", "application/octet-stream")) }, enabled = !importingServer, shape = McShape.ControlCompact) { Text("Tệp nén", fontSize = 11.sp) }
                                OutlinedButton(onClick = { jarImportLauncher.launch(arrayOf("application/java-archive", "application/octet-stream")) }, enabled = !importingServer, shape = McShape.ControlCompact) { Text("Tệp JAR", fontSize = 11.sp) }
                                OutlinedButton(onClick = { modpackImportLauncher.launch(arrayOf("application/octet-stream", "application/zip")) }, enabled = !importingServer, shape = McShape.ControlCompact) { Text("Modpack", fontSize = 11.sp) }
                            }
                            if (pendingImport != null) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = importName, onValueChange = { importName = it }, label = { Text("Tên server mới") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            if (importingServer) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { importProgress ?: 0f }, modifier = Modifier.fillMaxWidth())
                                Text("Đang nhập ${((importProgress ?: 0f) * 100).toInt()}%", color = Muted, fontSize = 11.sp)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = pendingImport != null && importName.isNotBlank() && !importingServer,
                            onClick = {
                                val selected = pendingImport ?: return@TextButton
                                when (selected.kind) {
                                    "folder" -> vm.importServerFromFolder(selected.uri, importName.trim())
                                    "jar" -> vm.importServerFromJar(selected.uri, importName.trim())
                                    "modpack" -> vm.importServerFromArchive(selected.uri, importName.trim())
                                    else -> vm.importServerFromArchive(selected.uri, importName.trim())
                                }
                            }
                        ) { Text("Bắt đầu nhập", color = Indigo) }
                    },
                    dismissButton = { TextButton(enabled = !importingServer, onClick = { showImportDialog = false; pendingImport = null }) { Text("Hủy", color = Muted) } }
                )
            }
        }
    }
}

private data class InstallerJavaRequirement(
    val recommendedJava: JavaVersion,
    val preferredVendor: JavaVendor,
    val exactLegacyForgeRequirement: Boolean,
    val reasonTextVi: String
)

private fun installerJavaRequirement(
    core: ServerCore,
    minecraftVersion: String
): InstallerJavaRequirement? {
    if (core == ServerCore.PocketMine) return null

    if (core.isBedrock || core == ServerCore.Velocity || core == ServerCore.BungeeCord) {
        return InstallerJavaRequirement(
            recommendedJava = JavaVersion.Java21,
            preferredVendor = JavaVendor.Termux,
            exactLegacyForgeRequirement = false,
            reasonTextVi = "Máy chủ ${core.displayName} yêu cầu Java 21 (LTS) để khởi động ổn định."
        )
    }

    val versionParts = minecraftVersion
        .trim()
        .removePrefix("v")
        .split('.')
        .mapNotNull { it.toIntOrNull() }
    val major = versionParts.getOrNull(0)
    val minor = versionParts.getOrNull(1)
    val patch = versionParts.getOrNull(2) ?: 0

    if (major == 26) {
        return InstallerJavaRequirement(
            recommendedJava = JavaVersion.Java25,
            preferredVendor = JavaVendor.Termux,
            exactLegacyForgeRequirement = false,
            reasonTextVi = "Minecraft $minecraftVersion (Thế hệ 26.x) khuyên dùng Java 25 (hoặc Java 21)."
        )
    }

    if (major != 1 || minor == null) {
        return InstallerJavaRequirement(
            recommendedJava = JavaVersion.Java21,
            preferredVendor = JavaVendor.Termux,
            exactLegacyForgeRequirement = false,
            reasonTextVi = "Khuyên dùng Java 21 (LTS) cho các phiên bản Minecraft hiện đại."
        )
    }

    if (core == ServerCore.Forge && (minor <= 12)) {
        return InstallerJavaRequirement(
            recommendedJava = JavaVersion.Java8,
            preferredVendor = JavaVendor.Proot,
            exactLegacyForgeRequirement = true,
            reasonTextVi = "Forge $minecraftVersion bắt buộc dùng đúng Java 8 (PRoot Ubuntu 20.04)."
        )
    }

    return when {
        minor <= 16 -> InstallerJavaRequirement(
            recommendedJava = JavaVersion.Java11,
            preferredVendor = JavaVendor.Termux,
            exactLegacyForgeRequirement = false,
            reasonTextVi = "Minecraft $minecraftVersion (1.7.10–1.16.5) tương thích tốt nhất với Java 11 hoặc Java 8."
        )
        minor > 20 || (minor == 20 && patch >= 5) -> InstallerJavaRequirement(
            recommendedJava = if (core.needsInstaller) JavaVersion.Java25 else JavaVersion.Java21,
            preferredVendor = JavaVendor.Termux,
            exactLegacyForgeRequirement = false,
            reasonTextVi = "Minecraft $minecraftVersion (1.20.5–1.21.11) yêu cầu tối thiểu Java 21 (hoặc Java 25 khi chạy trình cài Forge/NeoForge)."
        )
        else -> InstallerJavaRequirement(
            recommendedJava = JavaVersion.Java17,
            preferredVendor = JavaVendor.Termux,
            exactLegacyForgeRequirement = false,
            reasonTextVi = "Minecraft $minecraftVersion (1.17–1.20.4) yêu cầu Java 17 (LTS)."
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstallerJavaNotice(
    vm: McViewModel,
    core: ServerCore,
    minecraftVersion: String,
    installedJava: Set<JavaRuntime>,
    selectedJavaRuntime: JavaRuntime,
    javaOperation: String?,
    isServerRunning: Boolean
) {
    val requirement = installerJavaRequirement(core, minecraftVersion) ?: return
    var showJavaGuide by rememberSaveable { mutableStateOf(false) }

    val targetRuntime = JavaRuntime(requirement.preferredVendor, requirement.recommendedJava)
    // Với MC 1.20.5–1.21.11: cả Java 21 và Java 25 đều chạy tốt
    val acceptableVersions = remember(requirement.recommendedJava, minecraftVersion) {
        when (requirement.recommendedJava) {
            JavaVersion.Java21, JavaVersion.Java25 -> setOf(JavaVersion.Java21, JavaVersion.Java25)
            JavaVersion.Java11 -> setOf(JavaVersion.Java11, JavaVersion.Java8, JavaVersion.Java17)
            else -> setOf(requirement.recommendedJava)
        }
    }
    val matchingInstalledRuntime = installedJava.firstOrNull { it.version == requirement.recommendedJava }
        ?: installedJava.firstOrNull { it.version in acceptableVersions }
    val isCurrentlyCompatible = selectedJavaRuntime in installedJava && selectedJavaRuntime.version in acceptableVersions
    val busy = javaOperation != null

    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(McShape.Row)
            .background(
                when {
                    isCurrentlyCompatible -> Mint.copy(alpha = 0.14f)
                    matchingInstalledRuntime != null -> Indigo.copy(alpha = 0.16f)
                    else -> Coral.copy(alpha = 0.14f)
                }
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "☕ Môi trường Java cho ${core.displayName} $minecraftVersion",
                color = when {
                    isCurrentlyCompatible -> Mint
                    matchingInstalledRuntime != null -> Indigo
                    else -> Coral
                },
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                when {
                    isCurrentlyCompatible -> "✅ Đã sẵn sàng (${selectedJavaRuntime.version.displayName})"
                    matchingInstalledRuntime != null -> "⚠️ Cần chuyển sang ${matchingInstalledRuntime.version.displayName}"
                    else -> "❌ Chưa cài ${requirement.recommendedJava.displayName}"
                },
                color = when {
                    isCurrentlyCompatible -> Mint
                    matchingInstalledRuntime != null -> Indigo
                    else -> Coral
                },
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Text(
            requirement.reasonTextVi,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        if (javaOperation != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = Indigo
                )
                Text(
                    javaOperation,
                    color = Indigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (!isCurrentlyCompatible) {
            if (matchingInstalledRuntime != null) {
                Button(
                    onClick = { vm.setJavaRuntime(matchingInstalledRuntime) },
                    enabled = !busy && !isServerRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    shape = McShape.ControlCompact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "🔄 Chuyển sang dùng ${matchingInstalledRuntime.displayName} (Đã cài sẵn) ngay",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Button(
                    onClick = { vm.installJava(targetRuntime) },
                    enabled = !busy && !isServerRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    shape = McShape.ControlCompact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "⚡ Tự động tải & cài đặt ${targetRuntime.displayName} ngay (1 chạm)",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Text(
            if (showJavaGuide) "▲ Ẩn hướng dẫn cài & chọn môi trường Java"
            else "▼ 📖 Xem hướng dẫn chọn Java (Java 8 / 17 / 21 / 25) & tự cài đặt 1 chạm",
            color = Indigo,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { showJavaGuide = !showJavaGuide }
        )

        androidx.compose.animation.AnimatedVisibility(visible = showJavaGuide) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(McShape.Row)
                    .background(com.mineserve.mobile.ui.theme.FieldGray)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "📌 Bảng tra cứu nhanh Phiên bản Minecraft ↔ Môi trường Java:",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.5.sp,
                    color = Indigo
                )
                Text("• 🌟 Minecraft 1.20.5 → 1.21.11: Bắt buộc dùng Java 21 (hoặc Java 25).", fontSize = 10.5.sp, color = Muted)
                Text("• 🚀 Minecraft 26.1 / 26.2+: Dùng Java 25 (hoặc Java 21).", fontSize = 10.5.sp, color = Muted)
                Text("• ⚡ Minecraft 1.17 → 1.20.4 (1.20.1, 1.19.4, 1.18.2): Dùng Java 17.", fontSize = 10.5.sp, color = Muted)
                Text("• 🏛️ Minecraft 1.7.10 → 1.16.5 (1.16.5, 1.12.2, 1.8.8): Dùng Java 11 hoặc Java 8 (Forge 1.12.2 bắt buộc Java 8).", fontSize = 10.5.sp, color = Muted)

                Text(
                    "🛠️ Cài đặt nhanh các bản Java phổ biến ngay tại đây:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        JavaRuntime(JavaVendor.Termux, JavaVersion.Java21) to "Java 21 (1.20.5–1.21.11)",
                        JavaRuntime(JavaVendor.Termux, JavaVersion.Java17) to "Java 17 (1.17–1.20.4)",
                        JavaRuntime(JavaVendor.Termux, JavaVersion.Java11) to "Java 11 (1.8–1.16.5)",
                        JavaRuntime(JavaVendor.Termux, JavaVersion.Java25) to "Java 25 (Forge/26.x)",
                        JavaRuntime(JavaVendor.Proot, JavaVersion.Java8) to "Java 8 (Forge 1.12.2)"
                    ).forEach { (rt, label) ->
                        val isInst = rt in installedJava
                        val isSel = selectedJavaRuntime == rt
                        OutlinedButton(
                            onClick = {
                                if (isInst) vm.setJavaRuntime(rt) else vm.installJava(rt)
                            },
                            enabled = !busy && !isServerRunning,
                            shape = McShape.ControlCompact,
                            border = BorderStroke(1.dp, if (isSel) Mint else if (isInst) Indigo else Coral),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                when {
                                    isSel -> "✅ Đang dùng: $label"
                                    isInst -> "🔄 Chọn: $label"
                                    else -> "⬇️ Tải $label"
                                },
                                color = if (isSel) Mint else if (isInst) Indigo else Coral,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
