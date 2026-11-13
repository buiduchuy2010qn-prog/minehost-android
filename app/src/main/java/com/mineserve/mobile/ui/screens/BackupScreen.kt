package com.mineserve.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.R
import com.mineserve.mobile.data.AutoBackupType
import com.mineserve.mobile.server.BackupManager
import com.mineserve.mobile.ui.BackBar
import com.mineserve.mobile.ui.DebouncedTextField
import com.mineserve.mobile.ui.EmptyHint
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegPill
import com.mineserve.mobile.ui.SwipeableInstantSnackbarHost
import com.mineserve.mobile.ui.showInstantSnackbar
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.IndigoSoft
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ExtBackupFilter(val labelVi: String) {
    All("📦 Tất cả"),
    World("🗺️ Thế giới"),
    Mods("🧩 Bộ Mod"),
    Server("🖥️ Toàn Server"),
    Auto("⚡ Tự động")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(vm: McViewModel, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val glass = HorizonGlass
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
    var selectedBackupFilter by rememberSaveable { mutableStateOf(ExtBackupFilter.All) }

    val cloudFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) vm.setCloudSyncFolderUri(uri)
    }

    val hasStoragePerm = com.mineserve.mobile.server.ExternalBackupStore.hasPermission(context)
    var extBackups by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    fun refreshExtBackups() {
        extBackups = com.mineserve.mobile.server.ExternalBackupStore.listBackups()
    }
    LaunchedEffect(Unit) { refreshExtBackups() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.importBackupToExternal(it) }
    }

    var pendingDeleteBackup by remember { mutableStateOf<String?>(null) }
    var pendingRestoreSnapshot by remember { mutableStateOf<String?>(null) }
    var pendingDeleteSnapshot by remember { mutableStateOf<String?>(null) }
    var showPermDialog by remember { mutableStateOf(false) }
    var permDialogShown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasStoragePerm && !permDialogShown) {
            permDialogShown = true
            showPermDialog = true
        }
    }

    val openPermissionSettings: () -> Unit = {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:" + context.packageName)
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + context.packageName)
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    LaunchedEffect(Unit) { vm.loadSnapshots() }
    LaunchedEffect(Unit) { vm.errorFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) } }
    LaunchedEffect(Unit) { vm.messageFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) } }

    var exportTarget by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { target -> exportTarget?.let { vm.exportSnapshotToUri(it, target) } }
        exportTarget = null
    }

    val parsedExtBackups = remember(extBackups) {
        extBackups.map { f -> f to vm.externalBackupInfo(f) }
    }
    val filteredExtBackups = remember(parsedExtBackups, selectedBackupFilter) {
        parsedExtBackups.filter { (_, info) ->
            when (selectedBackupFilter) {
                ExtBackupFilter.All -> true
                ExtBackupFilter.World -> info.kind == BackupManager.BackupKind.World
                ExtBackupFilter.Mods -> info.kind == BackupManager.BackupKind.Mods
                ExtBackupFilter.Server -> info.kind == BackupManager.BackupKind.Server
                ExtBackupFilter.Auto -> info.origin == BackupManager.BackupOrigin.Automatic
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SwipeableInstantSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            BackBar(title = stringResource(R.string.s324), onBack = onBack)
            HeaderBlock(
                eyebrow = stringResource(R.string.eyebrow_backup),
                title = stringResource(R.string.s324),
                statusBarPadding = false
            )

            // ── Cảnh báo quyền bộ nhớ ngoài (nếu chưa cấp) ───────────────────────
            if (!hasStoragePerm) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = McSpacing.Page, vertical = 6.dp)
                        .horizonGlassPlate(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = glass.dangerCoralSoft,
                            borderColor = glass.dangerCoral.copy(alpha = 0.65f),
                            highlighted = true
                        )
                        .padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.backup_ext_perm_title),
                                color = glass.dangerCoral,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.backup_ext_perm_content),
                                color = Muted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Button(
                        onClick = { openPermissionSettings() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = McShape.Control,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                    ) {
                        Text(
                            stringResource(R.string.backup_ext_perm_action) + " →",
                            color = MaterialTheme.colorScheme.onError,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // ── 1. Sao lưu tự động khi đóng Server & Google Drive ────────────────
            McCard(title = "☁️ Tự động sao lưu Map + Mod khi Đóng Server & Google Drive") {
                Text(
                    "Mỗi khi bạn tắt/đóng Server, ứng dụng sẽ tự động nén Map hiện tại kèm thư mục mods/ & config/ và hỗ trợ lưu thẳng lên Google Drive.",
                    color = Muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.glassRow,
                            borderColor = glass.hairline
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "💾 Tự động sao lưu Map + Mod khi đóng Server",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Bật mặc định để không bao giờ mất tiến trình chơi",
                                fontSize = 10.5.sp,
                                color = Muted
                            )
                        }
                        Switch(
                            checked = autoBackupOnStop,
                            onCheckedChange = { vm.setAutoBackupOnStop(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = glass.accent,
                                checkedTrackColor = IndigoSoft
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "☁️ Tự mở Google Drive để lưu ngay khi đóng Server",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = glass.accent
                            )
                            Text(
                                "Tự động bật hộp thoại Lưu vào Google Drive sau khi đóng SV",
                                fontSize = 10.5.sp,
                                color = Muted
                            )
                        }
                        Switch(
                            checked = autoUploadDriveOnStop,
                            onCheckedChange = { vm.setAutoUploadDriveOnStop(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = glass.accent,
                                checkedTrackColor = IndigoSoft
                            )
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            vm.backupWorldToExternal(shareToDrive = true)
                            scope.launch { delay(800); refreshExtBackups() }
                        },
                        enabled = isBootstrapped,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = McShape.ControlCompact,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                    ) {
                        Text(
                            "☁️ Sao lưu Map + Mod lên Drive",
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = { cloudFolderLauncher.launch(null) },
                        border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.55f)),
                        shape = McShape.ControlCompact,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                    ) {
                        Text(
                            if (cloudSyncFolderUri != null) "📂 Đổi Thư mục Đồng bộ" else "📂 Chọn Thư mục Đồng bộ",
                            color = glass.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── 2. Tự động sao lưu định kỳ ───────────────────────────────────────
            McCard(title = "⏱️ Tự động sao lưu định kỳ") {
                Text(
                    "Chỉ tạo bản sao lưu khi máy chủ đang chạy và tự động dọn các bản sao lưu tự động cũ cùng loại.",
                    color = Muted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0 to "Tắt", 30 to "30 phút", 60 to "1 giờ", 180 to "3 giờ").forEach { (minutes, label) ->
                        SegPill(text = label, selected = config.autoBackupIntervalMin == minutes) {
                            vm.setAutoBackupInterval(minutes)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AutoBackupType.entries.forEach { type ->
                        SegPill(text = type.displayName, selected = config.autoBackupType == type) {
                            vm.setAutoBackupType(type)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DebouncedTextField(
                        value = if (config.autoBackupIntervalMin > 0) config.autoBackupIntervalMin.toString() else "",
                        onValueChange = { raw ->
                            raw.toIntOrNull()?.let { vm.setAutoBackupInterval(it.coerceIn(5, 10080)) }
                        },
                        sanitize = { it.filter(Char::isDigit) },
                        label = { Text("Chu kỳ (phút)", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = config.maxSnapshots.toString(),
                        onValueChange = { vm.setMaxSnapshots(it.toIntOrNull() ?: config.maxSnapshots) },
                        label = { Text("Số bản giữ lại", fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = McShape.Row,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = glass.glassInput,
                            unfocusedContainerColor = glass.glassInput,
                            focusedBorderColor = glass.accent,
                            unfocusedBorderColor = glass.hairline
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Chu kỳ từ 5–10080 phút; số bản giữ lại từ 1–100. Để tắt sao lưu tự động hãy chọn 'Tắt' ở trên.",
                    style = HorizonTabularStyle,
                    color = Muted,
                    fontSize = 10.5.sp
                )
            }

            // ── 3. Sao lưu thế giới thủ công & Bộ nhớ ngoài ──────────────────────
            McCard(title = "💾 Sao lưu thủ công & Bộ nhớ máy (/MineHost_SaoLuu)") {
                Text(
                    "Lưu trực tiếp ra bộ nhớ máy — không bao giờ bị mất khi gỡ hay cập nhật ứng dụng:",
                    color = Muted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            vm.sendCommand(
                                config.installedCores.find { it.name == config.activeCoreName }?.core?.consoleSaveCommand ?: "save-all"
                            )
                            scope.launch { snackbarHostState.showInstantSnackbar(context.getString(R.string.s327)) }
                        },
                        enabled = isBootstrapped,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = McShape.ControlCompact,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                    ) {
                        Text(
                            stringResource(R.string.s328),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            isSnapshotting = true
                            scope.launch {
                                val path = vm.createSnapshot()
                                isSnapshotting = false
                                val msg = if (path != null) context.getString(R.string.s329, path) else context.getString(R.string.s330)
                                snackbarHostState.showInstantSnackbar(msg)
                                refreshExtBackups()
                            }
                        },
                        enabled = isBootstrapped && !isSnapshotting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = McShape.ControlCompact,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                    ) {
                        if (isSnapshotting) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                MaterialTheme.colorScheme.onSecondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                stringResource(R.string.s331),
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.backup_ext_hint), color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            vm.backupWorldToExternal()
                            scope.launch { delay(700); refreshExtBackups() }
                        },
                        enabled = isBootstrapped && hasStoragePerm,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = McShape.ControlCompact,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Text(
                            stringResource(R.string.backup_ext_world),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            vm.backupServerToExternal()
                            scope.launch { delay(700); refreshExtBackups() }
                        },
                        enabled = isBootstrapped && hasStoragePerm,
                        border = BorderStroke(1.dp, glass.warnAmber),
                        shape = McShape.ControlCompact,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Text(
                            stringResource(R.string.backup_ext_server),
                            color = glass.warnAmber,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            vm.backupModsForCurrentServer(shareToDrive = false)
                            scope.launch { delay(700); refreshExtBackups() }
                        },
                        enabled = isBootstrapped,
                        border = BorderStroke(1.dp, glass.accent),
                        shape = McShape.ControlCompact,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Text(
                            "📦 Sao lưu riêng Bộ Mod",
                            color = glass.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            vm.backupModsForCurrentServer(shareToDrive = true)
                            scope.launch { delay(700); refreshExtBackups() }
                        },
                        enabled = isBootstrapped,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = McShape.ControlCompact,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Text(
                            "☁️ Sao lưu Mod lên Drive",
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.backup_ext_refresh_hint), color = Muted, fontSize = 10.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.backup_ext_rename_rule),
                    color = glass.warnAmber,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }

            // ── 4. Danh sách tệp sao lưu ngoài (Bộ lọc Segmented Pills + Tabular) ──
            McCard(
                title = stringResource(R.string.backup_ext_list_title, extBackups.size),
                trailing = {
                    Row {
                        IconButton(
                            onClick = { importLauncher.launch(arrayOf("application/zip")) },
                            modifier = Modifier.size(McIcon.Touch)
                        ) {
                            Icon(
                                Icons.Outlined.FileUpload,
                                stringResource(R.string.backup_ext_import),
                                tint = glass.accent,
                                modifier = Modifier.size(McIcon.Action)
                            )
                        }
                        IconButton(
                            onClick = { refreshExtBackups() },
                            modifier = Modifier.size(McIcon.Touch)
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                stringResource(R.string.s333),
                                tint = glass.accent,
                                modifier = Modifier.size(McIcon.Action)
                            )
                        }
                    }
                }
            ) {
                if (extBackups.isEmpty()) {
                    EmptyHint(icon = Icons.Outlined.Archive, text = stringResource(R.string.backup_ext_list_empty))
                } else {
                    val totalExtBytes = extBackups.sumOf { it.length() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.accentSoft,
                                borderColor = glass.accent.copy(alpha = 0.45f)
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "💾 Tổng ${extBackups.size} bản sao lưu",
                            style = HorizonTabularStyle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            formatBytesCompat(totalExtBytes),
                            style = HorizonTabularStyle,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = glass.accent
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ExtBackupFilter.entries, key = { it.name }) { filterItem ->
                            SegPill(
                                text = filterItem.labelVi,
                                selected = selectedBackupFilter == filterItem,
                                compact = true,
                                onClick = { selectedBackupFilter = filterItem }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
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
                            border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.55f)),
                            shape = McShape.ControlCompact,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                        ) {
                            Text(
                                "🧹 Giữ 1 bản tự động/SV",
                                style = HorizonTabularStyle,
                                color = glass.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                            border = BorderStroke(1.dp, glass.dangerCoral.copy(alpha = 0.6f)),
                            shape = McShape.ControlCompact,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                        ) {
                            Text(
                                "🗑️ Xóa sao lưu tự động",
                                color = glass.dangerCoral,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    if (filteredExtBackups.isEmpty()) {
                        Text(
                            "Không có bản sao lưu nào khớp bộ lọc「${selectedBackupFilter.labelVi}」.",
                            color = Muted,
                            fontSize = 11.5.sp
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredExtBackups.forEach { (f, info) ->
                                val (kindLabel, kindColor) = when (info.kind) {
                                    BackupManager.BackupKind.World -> "🗺️ Map + Mod" to glass.liveGreen
                                    BackupManager.BackupKind.Mods -> "🧩 Bộ Mod + Config" to glass.accent
                                    BackupManager.BackupKind.Server -> "🖥️ Toàn Server" to glass.skyCyan
                                }
                                val coreLabel = when (info.kind) {
                                    BackupManager.BackupKind.Server -> " · Core: ${info.coreTag ?: "—"}"
                                    BackupManager.BackupKind.Mods -> " · ${info.coreTag ?: "mods"}"
                                    BackupManager.BackupKind.World -> ""
                                }
                                val originLabel = when (info.origin) {
                                    BackupManager.BackupOrigin.Manual -> "Thủ công"
                                    BackupManager.BackupOrigin.Automatic -> "⚡ Tự động"
                                    null -> "Lịch sử"
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizonGlassPlate(
                                            shape = McShape.Row,
                                            containerColor = glass.glassRow,
                                            borderColor = glass.hairline
                                        )
                                        .padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            f.name,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(glass.glassInput)
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                formatBytesCompat(f.length()),
                                                style = HorizonTabularStyle,
                                                color = kindColor,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                    Text(
                                        "$kindLabel · $originLabel · SV: ${info.dirName ?: "—"}$coreLabel\n🕒 ${formatBackupTime(info.createdTime, f.lastModified())}",
                                        style = HorizonTabularStyle,
                                        color = Muted,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { vm.requestShareBackupToGoogleDrive(f.absolutePath) },
                                            border = BorderStroke(1.dp, glass.liveGreen),
                                            shape = McShape.ControlCompact,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 42.dp)
                                        ) {
                                            Text(
                                                "☁️ Drive",
                                                color = glass.liveGreen,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                when (info.kind) {
                                                    BackupManager.BackupKind.World -> vm.restoreWorldFromExternal(f.name)
                                                    BackupManager.BackupKind.Mods -> vm.restoreModsFromExternal(f.name)
                                                    BackupManager.BackupKind.Server -> vm.requestRestoreServer(f.name)
                                                }
                                            },
                                            enabled = isBootstrapped,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = McShape.ControlCompact,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .heightIn(min = 42.dp)
                                        ) {
                                            Text(
                                                "♻️ " + stringResource(R.string.backup_ext_restore),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        IconButton(
                                            onClick = { pendingDeleteBackup = f.name },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                stringResource(R.string.backup_ext_delete),
                                                tint = glass.dangerCoral,
                                                modifier = Modifier.size(McIcon.Action)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 5. Xóa thư mục thế giới (Reset World) ───────────────────────────
            McCard(title = stringResource(R.string.ui_world_delete)) {
                Text(stringResource(R.string.ui_world_delete_hint), color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showDeleteWorldConfirm = true },
                    enabled = isBootstrapped,
                    border = BorderStroke(1.dp, glass.dangerCoral),
                    shape = McShape.Control,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                ) {
                    Text(
                        stringResource(R.string.ui_world_delete),
                        color = glass.dangerCoral,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── 6. Bản sao lưu nội bộ cũ (Legacy Snapshots) ─────────────────────
            McCard(
                title = "Bản sao lưu nội bộ cũ (${snapshots.size})",
                trailing = {
                    IconButton(onClick = { vm.loadSnapshots() }, modifier = Modifier.size(McIcon.Touch)) {
                        Icon(
                            Icons.Outlined.Refresh,
                            stringResource(R.string.s333),
                            tint = glass.accent,
                            modifier = Modifier.size(McIcon.Action)
                        )
                    }
                }
            ) {
                if (snapshots.isEmpty()) {
                    EmptyHint(icon = Icons.Outlined.Archive, text = stringResource(R.string.s334))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        snapshots.forEach { snap ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizonGlassPlate(
                                        shape = McShape.Row,
                                        containerColor = glass.glassRow,
                                        borderColor = glass.hairline
                                    )
                                    .padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(snap.name, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${snap.sizeText} · ${snap.createdText}",
                                        style = HorizonTabularStyle,
                                        color = Muted,
                                        fontSize = 10.5.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        exportTarget = snap.name
                                        exportLauncher.launch(snap.name)
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.FileDownload,
                                        stringResource(R.string.s335),
                                        tint = glass.accent,
                                        modifier = Modifier.size(McIcon.Action)
                                    )
                                }
                                IconButton(
                                    onClick = { pendingRestoreSnapshot = snap.name },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Restore,
                                        stringResource(R.string.s337),
                                        tint = glass.liveGreen,
                                        modifier = Modifier.size(McIcon.Action)
                                    )
                                }
                                IconButton(
                                    onClick = { pendingDeleteSnapshot = snap.name },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        stringResource(R.string.s339),
                                        tint = glass.dangerCoral,
                                        modifier = Modifier.size(McIcon.Action)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            title = { Text(stringResource(R.string.backup_ext_perm_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.backup_ext_perm_content),
                    color = Muted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showPermDialog = false; openPermissionSettings() }) {
                    Text(stringResource(R.string.backup_ext_perm_action), color = glass.dangerCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermDialog = false }) {
                    Text(stringResource(R.string.s402), color = Muted)
                }
            }
        )
    }

    if (pendingDeleteBackup != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteBackup = null },
            title = { Text(stringResource(R.string.backup_ext_delete_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.backup_ext_delete_content, pendingDeleteBackup!!),
                    color = Muted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteExternalBackup(pendingDeleteBackup!!)
                    pendingDeleteBackup = null
                    scope.launch { delay(500); refreshExtBackups() }
                }) {
                    Text(stringResource(R.string.s401), color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBackup = null }) {
                    Text(stringResource(R.string.s402), color = Muted)
                }
            }
        )
    }

    pendingRestoreSnapshot?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingRestoreSnapshot = null },
            title = { Text(stringResource(R.string.backup_snapshot_restore_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.backup_snapshot_restore_content, name),
                    color = Muted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreSnapshot = null
                    scope.launch {
                        vm.restoreSnapshot(name)
                        snackbarHostState.showInstantSnackbar(context.getString(R.string.s336))
                    }
                }) {
                    Text(stringResource(R.string.s337), color = glass.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreSnapshot = null }) {
                    Text(stringResource(R.string.s402), color = Muted)
                }
            }
        )
    }

    pendingDeleteSnapshot?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSnapshot = null },
            title = { Text(stringResource(R.string.backup_snapshot_delete_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.backup_snapshot_delete_content, name),
                    color = Muted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteSnapshot = null
                    scope.launch {
                        vm.deleteSnapshot(name)
                        vm.loadSnapshots()
                        snackbarHostState.showInstantSnackbar(context.getString(R.string.s338, name))
                    }
                }) {
                    Text(stringResource(R.string.s339), color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSnapshot = null }) {
                    Text(stringResource(R.string.s402), color = Muted)
                }
            }
        )
    }

    val conflict by vm.restoreConflict.collectAsState()
    if (conflict != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissRestoreConflict() },
            title = { Text(stringResource(R.string.backup_conflict_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.backup_conflict_content, conflict!!.dirName),
                    color = Muted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmRestoreServer(true) }) {
                    Text(stringResource(R.string.backup_conflict_overwrite), color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.confirmRestoreServer(false) }) {
                    Text(stringResource(R.string.s402), color = Muted)
                }
            }
        )
    }

    if (showDeleteWorldConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteWorldConfirm = false },
            title = { Text(stringResource(R.string.ui_world_delete), fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.ui_world_delete_hint), fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteWorldConfirm = false
                    vm.deleteWorldDirs()
                }) { Text(stringResource(R.string.s339), color = glass.dangerCoral) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWorldConfirm = false }) {
                    Text(stringResource(R.string.s620))
                }
            }
        )
    }
}

private fun formatBytesCompat(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

private fun formatBackupTime(parsed: Long, modified: Long): String {
    val time = parsed.takeIf { it > 0 } ?: modified
    return if (time > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time)) else "Không rõ thời gian"
}
