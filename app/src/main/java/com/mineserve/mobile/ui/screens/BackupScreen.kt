package com.mineserve.mobile.ui.screens

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource
import com.mineserve.mobile.R

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.EmptyHint
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegPill
import com.mineserve.mobile.ui.DebouncedTextField
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.data.AutoBackupType
import com.mineserve.mobile.server.BackupManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(vm: McViewModel, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isBootstrapped by vm.isBootstrapped.collectAsState()
    val config by vm.config.collectAsState()
    val snapshots by vm.snapshots.collectAsState()
    val autoBackupOnStop by vm.autoBackupOnStop.collectAsState()
    val autoUploadDriveOnStop by vm.autoUploadDriveOnStop.collectAsState()
    val cloudSyncFolderUri by vm.cloudSyncFolderUri.collectAsState()
    var isSnapshotting by remember { mutableStateOf(false) }
    var showDeleteWorldConfirm by remember { mutableStateOf(false) }
    val cloudFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) vm.setCloudSyncFolderUri(uri)
    }
    // 外部备份权限（每次进入页面检查）
    val hasStoragePerm = com.mineserve.mobile.server.ExternalBackupStore.hasPermission(context)
    // 外部备份文件列表
    var extBackups by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    fun refreshExtBackups() {
        extBackups = com.mineserve.mobile.server.ExternalBackupStore.listBackups()
    }
    LaunchedEffect(Unit) { refreshExtBackups() }
    // 导入备份（SAF 选择手机上的 zip 复制到外部目录）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.importBackupToExternal(it) }
    }
    // 待删除的外部备份文件（二次确认）
    var pendingDeleteBackup by remember { mutableStateOf<String?>(null) }
    // 历史私有快照的「还原 / 删除」二次确认目标（破坏性操作，需明确确认）
    var pendingRestoreSnapshot by remember { mutableStateOf<String?>(null) }
    var pendingDeleteSnapshot by remember { mutableStateOf<String?>(null) }
    // 权限引导对话框（首次进入未授权弹一次）
    var showPermDialog by remember { mutableStateOf(false) }
    var permDialogShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasStoragePerm && !permDialogShown) {
            permDialogShown = true
            showPermDialog = true
        }
    }
    // 跳转系统「所有文件访问」设置页
    val openPermissionSettings: () -> Unit = {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:" + context.packageName)
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // 部分 ROM 无此 Activity，回退到应用详情设置
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + context.packageName)
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    LaunchedEffect(Unit) { vm.loadSnapshots() }
    // 收集错误/操作消息，修复"点击无响应"（此前未收集，还原/错误无任何反馈）
    LaunchedEffect(Unit) { vm.errorFlow.collectLatest { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.messageFlow.collectLatest { snackbarHostState.showSnackbar(it) } }

    // 快照导出到本地（SAF 自定义路径）
    var exportTarget by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { target -> exportTarget?.let { vm.exportSnapshotToUri(it, target) } }
        exportTarget = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // 嵌套在外层 McApp Scaffold 内：insets 已由外层消费，这里不再重复应用，避免顶部空白/白色遮挡
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState())
        ) {
            HeaderBlock(eyebrow = stringResource(R.string.eyebrow_backup), title = stringResource(R.string.s324))

            McCard(title = "☁️ Tự động sao lưu Map + Mod khi Đóng Server & Google Drive") {
                Text(
                    "Mỗi khi bạn tắt/đóng Server, ứng dụng sẽ tự động nén Map hiện tại kèm thư mục mods/ & config/ và hỗ trợ lưu thẳng lên Google Drive.",
                    color = Muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("💾 Tự động sao lưu Map + Mod khi đóng Server", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Bật mặc định để không bao giờ mất tiến trình chơi", fontSize = 11.5.sp, color = Muted)
                    }
                    androidx.compose.material3.Switch(
                        checked = autoBackupOnStop,
                        onCheckedChange = { vm.setAutoBackupOnStop(it) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("☁️ Tự mở Google Drive để lưu ngay khi đóng Server", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Indigo)
                        Text("Tự động bật hộp thoại Lưu vào Google Drive sau khi đóng SV", fontSize = 11.5.sp, color = Muted)
                    }
                    androidx.compose.material3.Switch(
                        checked = autoUploadDriveOnStop,
                        onCheckedChange = { vm.setAutoUploadDriveOnStop(it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            vm.backupWorldToExternal(shareToDrive = true)
                            scope.launch { delay(800); refreshExtBackups() }
                        },
                        enabled = isBootstrapped,
                        colors = ButtonDefaults.buttonColors(containerColor = com.mineserve.mobile.ui.theme.Mint),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("☁️ Sao lưu Map + Mod lên Drive", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { cloudFolderLauncher.launch(null) },
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (cloudSyncFolderUri != null) "📂 Đổi Thư mục Đồng bộ" else "📂 Chọn Thư mục Đồng bộ",
                            color = Indigo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            McCard(title = "Tự động sao lưu định kỳ") {
                Text("Chỉ tạo bản sao lưu khi máy chủ đang chạy và tự động dọn các bản sao lưu tự động cũ cùng loại.", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Tắt", 30 to "30 phút", 60 to "1 giờ", 180 to "3 giờ").forEach { (minutes, label) ->
                        SegPill(text = label, selected = config.autoBackupIntervalMin == minutes) {
                            vm.setAutoBackupInterval(minutes)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoBackupType.entries.forEach { type ->
                        SegPill(text = type.displayName, selected = config.autoBackupType == type) {
                            vm.setAutoBackupType(type)
                        }
                    }
                }
            }

            // 外部备份权限引导（未授权时显示，醒目样式）
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DebouncedTextField(
                    value = if (config.autoBackupIntervalMin > 0) config.autoBackupIntervalMin.toString() else "",
                    onValueChange = { raw ->
                        // 解析失败（空串/非数字）保持原值不提交，避免自动备份被静默写成 0（=关闭）；超界按 5–10080 分钟收敛
                        raw.toIntOrNull()?.let { vm.setAutoBackupInterval(it.coerceIn(5, 10080)) }
                    },
                    sanitize = { it.filter(Char::isDigit) },
                    label = { Text("Chu kỳ tùy chỉnh (phút)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(180.dp)
                )
                OutlinedTextField(
                    value = config.maxSnapshots.toString(),
                    onValueChange = { vm.setMaxSnapshots(it.toIntOrNull() ?: config.maxSnapshots) },
                    label = { Text("Số bản giữ lại") },
                    singleLine = true,
                    modifier = Modifier.width(150.dp)
                )
            }
            Text("Chu kỳ từ 5–10080 phút; số bản giữ lại từ 1–100. Để tắt sao lưu tự động hãy chọn 'Tắt' ở trên.", color = Muted, fontSize = 11.sp)

            if (!hasStoragePerm) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Coral.copy(alpha = 0.12f))
                        .border(1.dp, Coral, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.backup_ext_perm_title),
                                color = Coral, fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.backup_ext_perm_content),
                                color = Muted, fontSize = 11.sp, lineHeight = 15.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { openPermissionSettings() },
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                        shape = McShape.Control,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.backup_ext_perm_action) + " →",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 首次进入未授权：弹框强制引导
            if (showPermDialog) {
                AlertDialog(
                    onDismissRequest = { showPermDialog = false },
                    title = { Text(stringResource(R.string.backup_ext_perm_title), fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            stringResource(R.string.backup_ext_perm_content),
                            color = Muted, fontSize = 12.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showPermDialog = false; openPermissionSettings() }) {
                            Text(stringResource(R.string.backup_ext_perm_action), color = Coral, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermDialog = false }) {
                            Text(stringResource(R.string.s402), color = Muted)
                        }
                    }
                )
            }

            McCard(title = "Sao lưu thế giới thủ công") {
                Text("Lưu tại bộ nhớ máy: /storage/emulated/0/MineHost_SaoLuu/ (không bị mất khi gỡ ứng dụng)", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.sendCommand(
                                config.installedCores.find { it.name == config.activeCoreName }?.core?.consoleSaveCommand ?: "save-all"
                            )
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.s327)) }
                        },
                        enabled = isBootstrapped,
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        shape = McShape.ControlCompact, modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.s328), color = Color.White, fontSize = 12.sp) }
                    Button(
                        onClick = {
                            isSnapshotting = true
                            scope.launch {
                                val path = vm.createSnapshot()
                                isSnapshotting = false
                                val msg = if (path != null) context.getString(R.string.s329, path) else context.getString(R.string.s330)
                                snackbarHostState.showSnackbar(msg)
                                refreshExtBackups()
                            }
                        },
                        enabled = isBootstrapped && !isSnapshotting,
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        shape = McShape.ControlCompact, modifier = Modifier.weight(1f)
                    ) {
                        if (isSnapshotting) CircularProgressIndicator(Modifier.size(16.dp), Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.s331), color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            // 外部备份（保存到 /storage/emulated/0/世界与服务器的备份/，卸载保留）
            McCard(title = stringResource(R.string.backup_ext_title)) {
                Text(stringResource(R.string.backup_ext_hint), color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.backupWorldToExternal()
                            scope.launch { delay(700); refreshExtBackups() }
                        },
                        enabled = isBootstrapped && hasStoragePerm,
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        shape = McShape.ControlCompact, modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.backup_ext_world), color = Color.White, fontSize = 12.sp) }
                    Button(
                        onClick = {
                            vm.backupServerToExternal()
                            scope.launch { delay(700); refreshExtBackups() }
                        },
                        enabled = isBootstrapped && hasStoragePerm,
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                        shape = McShape.ControlCompact, modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.backup_ext_server), color = Color.White, fontSize = 12.sp) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.backupModsForCurrentServer(shareToDrive = false)
                            scope.launch { delay(700); refreshExtBackups() }
                        },
                        enabled = isBootstrapped,
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        shape = McShape.ControlCompact, modifier = Modifier.weight(1f)
                    ) { Text("📦 Sao lưu riêng Bộ Mod của Map/SV", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            vm.backupModsForCurrentServer(shareToDrive = true)
                            scope.launch { delay(700); refreshExtBackups() }
                        },
                        enabled = isBootstrapped,
                        colors = ButtonDefaults.buttonColors(containerColor = com.mineserve.mobile.ui.theme.Mint),
                        shape = McShape.ControlCompact, modifier = Modifier.weight(1f)
                    ) { Text("☁️ Sao lưu Mod lên Drive", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.backup_ext_refresh_hint), color = Muted, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.backup_ext_rename_rule),
                    color = Coral, fontSize = 10.sp, lineHeight = 14.sp
                )
            }

            // 外部备份文件列表（导入/还原/删除）
            McCard(
                title = stringResource(R.string.backup_ext_list_title, extBackups.size),
                trailing = {
                    Row {
                        IconButton(
                            onClick = { importLauncher.launch(arrayOf("application/zip")) },
                            modifier = Modifier.size(McIcon.Touch)
                        ) {
                            Icon(Icons.Outlined.FileUpload, stringResource(R.string.backup_ext_import), tint = Indigo, modifier = Modifier.size(McIcon.Action))
                        }
                        IconButton(
                            onClick = { refreshExtBackups() },
                            modifier = Modifier.size(McIcon.Touch)
                        ) {
                            Icon(Icons.Outlined.Refresh, stringResource(R.string.s333), tint = Indigo, modifier = Modifier.size(McIcon.Action))
                        }
                    }
                }
            ) {
                if (extBackups.isEmpty()) {
                    EmptyHint(icon = Icons.Outlined.Archive, text = stringResource(R.string.backup_ext_list_empty))
                } else {
                    val totalExtBytes = extBackups.sumOf { it.length() }
                    Text(
                        "💾 Tổng dung lượng ${extBackups.size} bản sao lưu: ${formatBytesCompat(totalExtBytes)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Indigo
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                vm.refreshLocalWorldArchives()
                                scope.launch {
                                    delay(300)
                                    vm.smartCleanupAutoBackups(keepPerServer = 1)
                                    delay(500)
                                    refreshExtBackups()
                                }
                            },
                            shape = McShape.ControlCompact,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🧹 Giữ 1 bản tự động mới nhất/SV", color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                vm.refreshLocalWorldArchives()
                                scope.launch {
                                    delay(300)
                                    vm.smartCleanupAutoBackups(keepPerServer = 0)
                                    delay(500)
                                    refreshExtBackups()
                                }
                            },
                            shape = McShape.ControlCompact,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🗑️ Xóa hết sao lưu tự động", color = Coral, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    extBackups.forEachIndexed { i, f ->
                        if (i > 0) Spacer(Modifier.height(6.dp))
                        val info = vm.externalBackupInfo(f)
                        val kindLabel = when (info.kind) {
                            BackupManager.BackupKind.World -> "🗺️ Sao lưu Map + Mod"
                            BackupManager.BackupKind.Mods -> "📦 Sao lưu Bộ Mod + Config"
                            BackupManager.BackupKind.Server -> "🖥️ Toàn bộ máy chủ"
                        }
                        val coreLabel = when (info.kind) {
                            BackupManager.BackupKind.Server -> " · Core: ${info.coreTag ?: "—"}"
                            BackupManager.BackupKind.Mods -> " · ${info.coreTag ?: "mods"}"
                            BackupManager.BackupKind.World -> ""
                        }
                        val originLabel = when (info.origin) {
                            BackupManager.BackupOrigin.Manual -> "Thủ công"
                            BackupManager.BackupOrigin.Automatic -> "Tự động"
                            null -> "Lịch sử"
                        }
                        val detail = "$kindLabel · $originLabel · Thư mục: ${info.dirName ?: "—"}" +
                            "$coreLabel · ${formatBackupTime(info.createdTime, f.lastModified())} · ${formatBytesCompat(f.length())}"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(detail, color = Muted, fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = { vm.requestShareBackupToGoogleDrive(f.absolutePath) }
                            ) {
                                Text("☁️ Drive", color = com.mineserve.mobile.ui.theme.Mint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            // 世界备份 → 还原世界；Mod 备份 → 还原 Mod；服务器备份 → 还原服务器（含重名检测）
                            TextButton(
                                onClick = {
                                    when (info.kind) {
                                        BackupManager.BackupKind.World -> vm.restoreWorldFromExternal(f.name)
                                        BackupManager.BackupKind.Mods -> vm.restoreModsFromExternal(f.name)
                                        BackupManager.BackupKind.Server -> vm.requestRestoreServer(f.name)
                                    }
                                },
                                enabled = isBootstrapped
                            ) {
                                Text(stringResource(R.string.backup_ext_restore), color = Indigo, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            // 删除备份（二次确认）
                            IconButton(
                                onClick = { pendingDeleteBackup = f.name },
                                modifier = Modifier.size(McIcon.TouchCompact)
                            ) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.backup_ext_delete), tint = Coral, modifier = Modifier.size(McIcon.Action))
                            }
                        }
                    }
                }
            }

            // 删除外部备份二次确认
            if (pendingDeleteBackup != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteBackup = null },
                    title = { Text(stringResource(R.string.backup_ext_delete_title), fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            stringResource(R.string.backup_ext_delete_content, pendingDeleteBackup!!),
                            color = Muted, fontSize = 12.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.deleteExternalBackup(pendingDeleteBackup!!)
                            pendingDeleteBackup = null
                            scope.launch { delay(500); refreshExtBackups() }
                        }) {
                            Text(stringResource(R.string.s401), color = Coral, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteBackup = null }) {
                            Text(stringResource(R.string.s402), color = Muted)
                        }
                    }
                )
            }

            // 私有快照还原二次确认（会用旧快照覆盖当前服务器数据）
            pendingRestoreSnapshot?.let { name ->
                AlertDialog(
                    onDismissRequest = { pendingRestoreSnapshot = null },
                    title = { Text(stringResource(R.string.backup_snapshot_restore_title), fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            stringResource(R.string.backup_snapshot_restore_content, name),
                            color = Muted, fontSize = 12.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingRestoreSnapshot = null
                            scope.launch {
                                vm.restoreSnapshot(name)
                                snackbarHostState.showSnackbar(context.getString(R.string.s336))
                            }
                        }) {
                            Text(stringResource(R.string.s337), color = Indigo, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingRestoreSnapshot = null }) {
                            Text(stringResource(R.string.s402), color = Muted)
                        }
                    }
                )
            }

            // 私有快照删除二次确认
            pendingDeleteSnapshot?.let { name ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteSnapshot = null },
                    title = { Text(stringResource(R.string.backup_snapshot_delete_title), fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            stringResource(R.string.backup_snapshot_delete_content, name),
                            color = Muted, fontSize = 12.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingDeleteSnapshot = null
                            scope.launch {
                                vm.deleteSnapshot(name)
                                vm.loadSnapshots()
                                snackbarHostState.showSnackbar(context.getString(R.string.s338, name))
                            }
                        }) {
                            Text(stringResource(R.string.s339), color = Coral, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteSnapshot = null }) {
                            Text(stringResource(R.string.s402), color = Muted)
                        }
                    }
                )
            }

            // 服务器还原重名冲突对话框
            val conflict by vm.restoreConflict.collectAsState()
            if (conflict != null) {
                AlertDialog(
                    onDismissRequest = { vm.dismissRestoreConflict() },
                    title = { Text(stringResource(R.string.backup_conflict_title), fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            stringResource(R.string.backup_conflict_content, conflict!!.dirName),
                            color = Muted, fontSize = 12.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { vm.confirmRestoreServer(true) }) {
                            Text(stringResource(R.string.backup_conflict_overwrite), color = Coral, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.confirmRestoreServer(false) }) {
                            Text(stringResource(R.string.s402), color = Muted)
                        }
                    }
                )
            }

            // 世界管理（删除世界文件夹）
            McCard(title = stringResource(R.string.ui_world_delete)) {                Text(stringResource(R.string.ui_world_delete_hint), color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showDeleteWorldConfirm = true },
                    enabled = isBootstrapped,
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    shape = McShape.Control
                ) {
                    Text(stringResource(R.string.ui_world_delete), color = Color.White, fontSize = 12.sp)
                }
            }

            // 旧应用私有快照仅保留读取、恢复和删除能力，不再创建新文件。
            McCard(
                title = "Bản sao lưu nội bộ cũ (${snapshots.size})",
                trailing = {
                    IconButton(onClick = { vm.loadSnapshots() }, modifier = Modifier.size(McIcon.Touch)) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.s333), tint = Indigo, modifier = Modifier.size(McIcon.Action))
                    }
                }
            ) {
                if (snapshots.isEmpty()) {
                    EmptyHint(icon = Icons.Outlined.Archive, text = stringResource(R.string.s334))
                } else {
                    snapshots.forEachIndexed { i, snap ->
                        if (i > 0) Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(snap.name, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${snap.sizeText} · ${snap.createdText}", color = Muted, fontSize = 10.sp)
                            }
                            IconButton(
                                onClick = {
                                    exportTarget = snap.name
                                    exportLauncher.launch(snap.name)
                                },
                                modifier = Modifier.size(McIcon.TouchCompact)
                            ) {
                                Icon(Icons.Outlined.FileDownload, stringResource(R.string.s335), tint = Indigo, modifier = Modifier.size(McIcon.Action))
                            }
                            IconButton(onClick = { pendingRestoreSnapshot = snap.name }, modifier = Modifier.size(McIcon.TouchCompact)) {
                                Icon(Icons.Outlined.Restore, stringResource(R.string.s337), tint = Indigo, modifier = Modifier.size(McIcon.Action))
                            }
                            IconButton(onClick = { pendingDeleteSnapshot = snap.name }, modifier = Modifier.size(McIcon.TouchCompact)) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.s339), tint = Coral, modifier = Modifier.size(McIcon.Action))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // 删除世界文件夹二次确认
    if (showDeleteWorldConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteWorldConfirm = false },
            title = { Text(stringResource(R.string.ui_world_delete), fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.ui_world_delete_hint), fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteWorldConfirm = false
                    vm.deleteWorldDirs()
                }) { Text(stringResource(R.string.s339), color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWorldConfirm = false }) {
                    Text(stringResource(R.string.s620))
                }
            }
        )
    }
}

/** 友好显示文件大小 */
private fun formatBytesCompat(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

private fun formatBackupTime(parsed: Long, modified: Long): String {
    val time = parsed.takeIf { it > 0 } ?: modified
    return if (time > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time)) else "Không rõ thời gian"
}
