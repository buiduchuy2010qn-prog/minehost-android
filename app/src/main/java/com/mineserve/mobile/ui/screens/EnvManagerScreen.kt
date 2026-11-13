package com.mineserve.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.R
import com.mineserve.mobile.data.JavaRuntime
import com.mineserve.mobile.data.JavaVendor
import com.mineserve.mobile.data.PhpVersion
import com.mineserve.mobile.data.StepStatus
import com.mineserve.mobile.ui.BackBar
import com.mineserve.mobile.ui.HintNote
import com.mineserve.mobile.ui.JavaRuntimeGroupList
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegmentedTabs
import com.mineserve.mobile.ui.StepRow
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

const val ENV_TAB_JAVA = 0
const val ENV_TAB_DEPS = 1
const val ENV_TAB_TERMUX = 2
const val ENV_TAB_PHP = 3

@Composable
fun EnvManagerScreen(vm: McViewModel, initialTab: Int, onBack: () -> Unit) {
    val glass = HorizonGlass
    var tab by remember { mutableStateOf(initialTab.coerceIn(0, 3)) }
    var confirmText by remember { mutableStateOf<String?>(null) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.refreshPhp() }
    LaunchedEffect(Unit) {
        vm.errorFlow.collectLatest { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.messageFlow.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    fun requestConfirm(text: String, action: () -> Unit) {
        confirmText = text
        confirmAction = action
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(padding)
        ) {
            BackBar(stringResource(R.string.env_title), onBack)
            val envTabs = listOf(
                stringResource(R.string.env_tab_java),
                stringResource(R.string.env_tab_deps),
                stringResource(R.string.env_tab_termux),
                stringResource(R.string.env_tab_php)
            )
            SegmentedTabs(
                options = envTabs,
                selectedIndex = tab,
                modifier = Modifier.padding(horizontal = McSpacing.Page, vertical = 6.dp),
                onSelect = { tab = it }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                McCard(title = "📖 Hướng dẫn cài đặt & Khắc phục lỗi") {
                    val guideText = when (tab) {
                        ENV_TAB_JAVA -> "• Bước 1: Qua thẻ 'Termux' cài Gói cơ sở trước nếu trạng thái báo chưa cài.\n• Bước 2: Chọn đúng bản Java theo phiên bản Minecraft: MC 1.20.5+ dùng Java 21; MC 1.18–1.20.4 dùng Java 17; MC 1.16.5 trở xuống dùng Java 8.\n• Cách sửa lỗi tải/cài Java: Kéo xuống thẻ 'Nguồn tải' -> bấm 'Tự động chọn nguồn nhanh nhất' rồi bấm Cài đặt lại."
                        ENV_TAB_DEPS -> "• Wget & Rclone là công cụ phụ trợ tải tệp và đồng bộ đám mây; PRoot chỉ cần khi bạn muốn chạy môi trường Ubuntu.\n• Nếu cài báo lỗi mạng/403: Hãy sang thẻ 'Termux' -> chọn nguồn 'Termux CDN (Quốc tế)' rồi thử lại."
                        ENV_TAB_PHP -> "• PHP 8 chỉ dành cho máy chủ PocketMine-MP (Bedrock). Nếu bạn chạy Minecraft Java (Paper, Fabric, Forge...) thì KHÔNG cần cài PHP."
                        else -> "• Đây là môi trường nền tảng bắt buộc. Hãy bấm 'Cài đặt' ở thẻ Môi trường cơ sở Termux trước tiên.\n• Nếu gặp lỗi thiếu thư viện hoặc quyền truy cập khi bật server: Bấm nút 'Sửa chữa môi trường' bên dưới để ứng dụng tự động khắc phục."
                    }
                    Text(guideText, color = Muted, fontSize = 11.5.sp, lineHeight = 16.5.sp)
                }
                when (tab) {
                    ENV_TAB_JAVA -> JavaModule(vm, ::requestConfirm)
                    ENV_TAB_DEPS -> DependenciesModule(vm, ::requestConfirm)
                    ENV_TAB_PHP -> PhpModule(vm, ::requestConfirm)
                    else -> TermuxModule(vm, ::requestConfirm)
                }
            }
        }
    }

    confirmText?.let { text ->
        AlertDialog(
            onDismissRequest = { confirmText = null; confirmAction = null },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            title = { Text(stringResource(R.string.env_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(text, color = Muted, fontSize = 12.sp, lineHeight = 16.5.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmText = null
                    confirmAction?.invoke()
                    confirmAction = null
                }) { Text(stringResource(R.string.env_confirm_ok), color = glass.dangerCoral, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmText = null; confirmAction = null }) {
                    Text(stringResource(R.string.env_cancel), color = Muted)
                }
            }
        )
    }
}

@Composable
private fun ActionButtonsRow(
    uninstallEnabled: Boolean,
    reinstallEnabled: Boolean,
    installEnabled: Boolean,
    onUninstall: () -> Unit,
    onReinstall: () -> Unit,
    onInstall: () -> Unit,
    reinstallLabelRes: Int = R.string.env_reinstall
) {
    val glass = HorizonGlass
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onUninstall,
            enabled = uninstallEnabled,
            shape = McShape.ControlCompact,
            border = BorderStroke(1.dp, glass.dangerCoral.copy(alpha = if (uninstallEnabled) 0.5f else 0.2f)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(R.string.env_uninstall), color = glass.dangerCoral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        OutlinedButton(
            onClick = onReinstall,
            enabled = reinstallEnabled,
            shape = McShape.ControlCompact,
            border = BorderStroke(1.dp, glass.hairline),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(reinstallLabelRes), color = glass.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        Button(
            onClick = onInstall,
            enabled = installEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = glass.accent,
                contentColor = Color.White
            ),
            shape = McShape.ControlCompact,
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(R.string.env_install), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun OperationBadge(operation: String?) {
    if (operation == null) return
    val glass = HorizonGlass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(McShape.Row)
            .background(glass.accentSoft)
            .border(1.dp, glass.accent.copy(alpha = 0.45f), McShape.Row)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = glass.accent)
        Spacer(Modifier.width(8.dp))
        Text(operation, style = HorizonTabularStyle, color = glass.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun JavaModule(vm: McViewModel, requestConfirm: (String, () -> Unit) -> Unit) {
    val glass = HorizonGlass
    val installed by vm.installedJava.collectAsState()
    val operation by vm.javaOperation.collectAsState()
    val inFlight by vm.javaInFlight.collectAsState()
    val serverState by vm.serverState.collectAsState()
    val config by vm.config.collectAsState()
    val selected = config.selectedJavaRuntime
    val busy = operation != null
    val running = serverState.isRunning
    val javaReinstallConfirmText = stringResource(R.string.env_java_reinstall_confirm)
    val javaUninstallConfirmText = stringResource(
        R.string.env_java_uninstall_confirm,
        selected.displayName
    )

    LaunchedEffect(Unit) { vm.refreshJava() }
    LaunchedEffect(Unit) { vm.refreshProotContainerSizes() }

    McCard(title = stringResource(R.string.env_tab_java)) {
        Text(stringResource(R.string.env_java_hint), color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        if (running) {
            Text(stringResource(R.string.env_java_running_block), color = glass.dangerCoral, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
        }
        OperationBadge(operation)
        var expandedVendors by remember { mutableStateOf(emptySet<JavaVendor>()) }
        JavaRuntimeGroupList(
            installed = installed,
            selected = selected,
            onSelect = { vm.setJavaRuntime(it) },
            enabled = !busy && !running,
            inFlight = inFlight,
            expandedVendors = expandedVendors,
            onToggleVendor = { vendor ->
                expandedVendors = if (vendor in expandedVendors) {
                    expandedVendors - vendor
                } else {
                    expandedVendors + vendor
                }
            },
            prootDistro = config.prootDistro,
            onDistroChange = { distro ->
                vm.applyRuntimeSelection(
                    kind = config.runtimeKind,
                    javaRuntime = JavaRuntime(JavaVendor.Proot, selected.version),
                    phpVersion = config.selectedPhpVersion,
                    prootDistro = distro
                )
            }
        )
        Spacer(Modifier.height(10.dp))
        ActionButtonsRow(
            uninstallEnabled = !busy && !running && selected in installed,
            reinstallEnabled = !busy && !running && selected in installed,
            installEnabled = !busy && !running && selected !in installed,
            onUninstall = {
                requestConfirm(javaUninstallConfirmText) { vm.deleteJava(selected) }
            },
            onReinstall = {
                requestConfirm(javaReinstallConfirmText) { vm.reinstallJava(selected) }
            },
            onInstall = { vm.installJava(selected) }
        )

        if (selected.vendor == JavaVendor.Proot) {
            val distro = config.prootDistro
            val sizes by vm.prootContainerSizes.collectAsState()
            val sizeText = formatBytes(sizes[distro] ?: 0L)
            val confirmText = stringResource(
                R.string.env_proot_delete_container_confirm,
                distro.displayName,
                sizeText
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { requestConfirm(confirmText) { vm.deleteProotContainer(distro) } },
                enabled = !busy && !running,
                shape = McShape.ControlCompact,
                border = BorderStroke(1.dp, glass.dangerCoral.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.dangerCoralSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.env_proot_delete_container_action),
                    color = glass.dangerCoral,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DependenciesModule(vm: McViewModel, requestConfirm: (String, () -> Unit) -> Unit) {
    val glass = HorizonGlass
    val isBootstrapped by vm.isBootstrapped.collectAsState()
    val isInstalling by vm.isInstalling.collectAsState()
    val installSpeed by vm.installSpeed.collectAsState()
    val serverState by vm.serverState.collectAsState()
    val depsUninstallConfirmText = stringResource(R.string.env_deps_uninstall_confirm)
    val depsReinstallConfirmText = stringResource(R.string.env_deps_reinstall_confirm)
    val steps = remember(serverState.installSteps) {
        serverState.installSteps.filter { it.step != com.mineserve.mobile.data.InstallStep.Jdk }
    }
    val anyInstalled = remember(steps) { steps.any { it.status == StepStatus.Done } }

    McCard(title = stringResource(R.string.env_tab_deps)) {
        Text(stringResource(R.string.env_deps_hint), color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        if (isInstalling) {
            OperationBadge(
                "${stringResource(R.string.env_busy)} " +
                    if (installSpeed > 0) formatSpeed(installSpeed) else ""
            )
        }
        if (!isBootstrapped) {
            Text(stringResource(R.string.env_deps_need_bootstrap), color = glass.dangerCoral, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
        }
        steps.forEachIndexed { idx, step ->
            val tag = when (step.status) {
                StepStatus.Done -> stringResource(R.string.s361)
                StepStatus.Active -> stringResource(R.string.s362)
                StepStatus.Wait -> stringResource(R.string.s363)
            }
            StepRow(name = "${idx + 1}. ${step.step.label}", status = step.status, tag = tag)
        }
        if (steps.isNotEmpty() && !anyInstalled) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.env_deps_none_installed), color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        ActionButtonsRow(
            uninstallEnabled = !isInstalling && anyInstalled,
            reinstallEnabled = !isInstalling && isBootstrapped,
            installEnabled = !isInstalling && isBootstrapped,
            onUninstall = {
                requestConfirm(depsUninstallConfirmText) {
                    vm.deleteDependencies()
                }
            },
            onReinstall = {
                requestConfirm(depsReinstallConfirmText) { vm.reinstallDependencies() }
            },
            onInstall = { vm.installDependencies() }
        )

        Spacer(Modifier.height(16.dp))
        FrpcEngineSection(vm = vm)
    }
}

@Composable
private fun FrpcEngineSection(vm: McViewModel) {
    val glass = HorizonGlass
    val engine by vm.frpcEngine.collectAsState()
    val message by vm.frpcEngineMessage.collectAsState()
    val download by vm.frpcDownload.collectAsState()

    LaunchedEffect(Unit) { vm.refreshFrpcEngine() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importFrpcEngine) }

    var confirmUninstall by remember { mutableStateOf(false) }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            title = { Text(stringResource(R.string.frpc_engine_uninstall), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.frpc_engine_uninstall_confirm), fontSize = 12.5.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    vm.uninstallFrpcEngine()
                }) { Text(stringResource(R.string.env_uninstall), color = glass.dangerCoral, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) {
                    Text(stringResource(R.string.env_cancel), color = Muted)
                }
            }
        )
    }

    McCard(title = stringResource(R.string.frpc_engine_title)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizonGlassPlate(
                    shape = McShape.Row,
                    containerColor = if (engine.installed) glass.liveGreenSoft else glass.dangerCoralSoft,
                    borderColor = if (engine.installed) glass.liveGreen.copy(alpha = 0.45f) else glass.dangerCoral.copy(alpha = 0.45f)
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (engine.installed) glass.liveGreen else glass.dangerCoral)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(
                    if (engine.installed) R.string.frpc_engine_installed
                    else R.string.frpc_engine_missing
                ),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (engine.installed) glass.liveGreen else glass.dangerCoral
            )
            engine.version?.let { version ->
                Spacer(Modifier.size(8.dp))
                Text("v$version", style = HorizonTabularStyle, color = Muted, fontSize = 11.sp)
            }
            engine.sizeBytes.takeIf { it > 0 }?.let { size ->
                Spacer(Modifier.size(6.dp))
                Text("${size / 1024 / 1024} MB", style = HorizonTabularStyle, color = Muted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.frpc_engine_hint), color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp)

        engine.sha256?.let { sha ->
            Spacer(Modifier.height(4.dp))
            Text(
                "SHA-256: ${sha.take(16)}…",
                style = HorizonTabularStyle,
                fontFamily = FontFamily.Monospace,
                color = Muted,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.frpcOnlineAvailable) {
                val downloading = download is McViewModel.DownloadState.Downloading
                Button(
                    onClick = { vm.downloadFrpcEngine() },
                    enabled = !downloading,
                    colors = ButtonDefaults.buttonColors(containerColor = glass.accent, contentColor = Color.White),
                    shape = McShape.ControlCompact
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            (download as? McViewModel.DownloadState.Downloading)
                                ?.fraction?.takeIf { it >= 0f }
                                ?.let { "${(it * 100).toInt()}%" }
                                ?: stringResource(R.string.frpc_engine_downloading),
                            style = HorizonTabularStyle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            stringResource(
                                if (engine.installed) R.string.frpc_engine_reinstall
                                else R.string.frpc_engine_download
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                border = BorderStroke(1.dp, glass.hairline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
                shape = McShape.ControlCompact
            ) {
                Text(
                    stringResource(
                        if (engine.installed) R.string.frpc_engine_replace
                        else R.string.frpc_engine_import
                    ),
                    fontSize = 12.sp,
                    color = glass.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (engine.installed) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { confirmUninstall = true }) {
                Text(stringResource(R.string.frpc_engine_uninstall), fontSize = 12.sp, color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.frpc_engine_where), color = Muted, fontSize = 10.5.sp)

        message?.let { text ->
            Spacer(Modifier.height(8.dp))
            HintNote(text)
            LaunchedEffect(text) {
                delay(4000)
                vm.clearFrpcEngineMessage()
            }
        }
    }
}

@Composable
private fun PhpModule(vm: McViewModel, requestConfirm: (String, () -> Unit) -> Unit) {
    val glass = HorizonGlass
    val isBootstrapped by vm.isBootstrapped.collectAsState()
    val isInstalling by vm.isInstalling.collectAsState()
    val serverState by vm.serverState.collectAsState()
    val installedPhp by vm.installedPhp.collectAsState()
    val selectedPhpVersion by vm.selectedPhpVersion.collectAsState()
    val busy = isInstalling || serverState.isRunning
    val phpUninstallConfirm = stringResource(R.string.env_php_uninstall_confirm)

    LaunchedEffect(Unit) { vm.refreshPhp() }

    var showPhpVersionDialog by remember { mutableStateOf(false) }
    McCard(title = stringResource(R.string.env_php_version_title)) {
        Text(stringResource(R.string.env_php_version_desc), color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizonGlassPlate(
                    shape = McShape.Row,
                    containerColor = glass.glassRow,
                    borderColor = glass.accent.copy(alpha = 0.45f)
                )
                .clickable { showPhpVersionDialog = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    selectedPhpVersion.displayName,
                    style = HorizonTabularStyle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.accent
                )
                selectedPhpVersion.noteRes?.let { note ->
                    Text(stringResource(note), color = Muted, fontSize = 10.5.sp)
                }
            }
            val isPhpInstalled = selectedPhpVersion in installedPhp
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isPhpInstalled) glass.liveGreenSoft else glass.glassInput)
                    .border(1.dp, if (isPhpInstalled) glass.liveGreen.copy(alpha = 0.45f) else glass.hairline, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    if (isPhpInstalled) "✓ ${stringResource(R.string.env_php_installed)}"
                    else stringResource(R.string.env_php_not_installed),
                    color = if (isPhpInstalled) glass.liveGreen else Muted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = stringResource(R.string.env_php_version_title),
                tint = glass.accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showPhpVersionDialog) {
        AlertDialog(
            onDismissRequest = { showPhpVersionDialog = false },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            title = { Text(stringResource(R.string.env_php_version_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.env_php_version_desc), color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    PhpVersion.entries.forEach { version ->
                        val installed = version in installedPhp
                        val isSelected = version == selectedPhpVersion
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = if (isSelected) glass.accentSoft else glass.glassRow,
                                    borderColor = if (isSelected) glass.accent else glass.hairline,
                                    highlighted = isSelected
                                )
                                .clickable {
                                    vm.setPhpVersion(version)
                                    showPhpVersionDialog = false
                                }
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    version.displayName,
                                    style = HorizonTabularStyle,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) glass.accent else Color.Unspecified
                                )
                                version.noteRes?.let { note ->
                                    Text(stringResource(note), color = Muted, fontSize = 10.sp)
                                }
                            }
                            Text(
                                if (installed) "✓ ${stringResource(R.string.env_php_installed)}"
                                else stringResource(R.string.env_php_not_installed),
                                color = if (installed) glass.liveGreen else Muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhpVersionDialog = false }) {
                    Text(stringResource(R.string.env_confirm_ok), color = glass.accent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    McCard(title = stringResource(R.string.env_php_title)) {
        Text(stringResource(R.string.env_php_hint), color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        if (serverState.isRunning) {
            Text(stringResource(R.string.env_php_running_block), color = glass.dangerCoral, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    selectedPhpVersion.displayName,
                    style = HorizonTabularStyle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.accent
                )
                selectedPhpVersion.noteRes?.let { note ->
                    Text(stringResource(note), color = Muted, fontSize = 10.5.sp)
                }
            }
            Text(
                if (selectedPhpVersion in installedPhp) "✓ ${stringResource(R.string.env_php_installed)}"
                else stringResource(R.string.env_php_not_installed),
                color = if (selectedPhpVersion in installedPhp) glass.liveGreen else Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (isInstalling) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = glass.accent)
            }
        }
        Spacer(Modifier.height(10.dp))
        ActionButtonsRow(
            uninstallEnabled = !busy && (selectedPhpVersion in installedPhp),
            reinstallEnabled = !busy && isBootstrapped && (selectedPhpVersion in installedPhp),
            installEnabled = !busy && isBootstrapped && (selectedPhpVersion !in installedPhp),
            onUninstall = {
                requestConfirm(phpUninstallConfirm) { vm.deletePhp(selectedPhpVersion) }
            },
            onReinstall = {
                requestConfirm(phpUninstallConfirm) { vm.deletePhp(selectedPhpVersion) }
            },
            onInstall = { vm.installPhp(selectedPhpVersion) },
            reinstallLabelRes = R.string.env_php_delete_action
        )
    }
}

@Composable
private fun TermuxModule(vm: McViewModel, requestConfirm: (String, () -> Unit) -> Unit) {
    val glass = HorizonGlass
    val isBootstrapped by vm.isBootstrapped.collectAsState()
    val bootstrapError by vm.bootstrapError.collectAsState()
    val bootstrapSpeed by vm.bootstrapSpeed.collectAsState()
    val serverState by vm.serverState.collectAsState()
    val termuxUninstallConfirmText = stringResource(R.string.env_termux_uninstall_confirm)
    val termuxReinstallConfirmText = stringResource(R.string.env_termux_reinstall_confirm)
    val progress = serverState.currentProgress
    val serverRunning = serverState.isRunning
    val busy = bootstrapError == null && !isBootstrapped && progress in 1..99

    McCard(title = stringResource(R.string.env_tab_termux)) {
        if (serverRunning) {
            Text(stringResource(R.string.env_running_block), color = glass.dangerCoral, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizonGlassPlate(
                    shape = McShape.Row,
                    containerColor = if (isBootstrapped) glass.liveGreenSoft else glass.glassRow,
                    borderColor = if (isBootstrapped) glass.liveGreen.copy(alpha = 0.45f) else glass.hairline
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isBootstrapped) stringResource(R.string.env_termux_ready) else stringResource(R.string.env_termux_missing),
                color = if (isBootstrapped) glass.liveGreen else Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = glass.accent)
            }
        }
        if (!isBootstrapped && progress in 1..99) {
            Spacer(Modifier.height(6.dp))
            Text(
                "$progress%" + if (bootstrapSpeed > 0) " · ${formatSpeed(bootstrapSpeed)}" else "",
                style = HorizonTabularStyle,
                color = glass.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        bootstrapError?.let {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.env_termux_error, it), color = glass.dangerCoral, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        ActionButtonsRow(
            uninstallEnabled = !serverRunning && !busy,
            reinstallEnabled = !serverRunning && !busy && isBootstrapped,
            installEnabled = !serverRunning && !busy && !isBootstrapped,
            onUninstall = {
                requestConfirm(termuxUninstallConfirmText) {
                    vm.forceDeleteBootstrap()
                }
            },
            onReinstall = {
                requestConfirm(termuxReinstallConfirmText) {
                    vm.deleteBootstrap()
                }
            },
            onInstall = { vm.retryBootstrap() }
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
    bytesPerSec >= 1024 -> "%.0f KB/s".format(bytesPerSec / 1024.0)
    else -> "$bytesPerSec B/s"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
