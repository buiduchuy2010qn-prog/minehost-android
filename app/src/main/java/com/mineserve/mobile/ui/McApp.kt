package com.mineserve.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import com.mineserve.mobile.ui.screens.MineHostOverview
import com.mineserve.mobile.ui.screens.MineHostManage
import com.mineserve.mobile.ui.screens.MineHostCrossplay
import com.mineserve.mobile.ui.screens.MineHostRemote
import com.mineserve.mobile.ui.screens.MineHostPerformance
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mineserve.mobile.ui.screens.BackupScreen
import com.mineserve.mobile.ui.screens.DiagnosticsScreen
import com.mineserve.mobile.ui.screens.DownloadHelpScreen
import com.mineserve.mobile.ui.screens.DownloadScreen
import com.mineserve.mobile.ui.screens.FileManagerScreen
import com.mineserve.mobile.ui.screens.KeepAliveScreen
import com.mineserve.mobile.ui.screens.MtGuideScreen
import com.mineserve.mobile.ui.screens.OpLevelGuideScreen
import com.mineserve.mobile.ui.screens.LogsScreen
import com.mineserve.mobile.ui.screens.FrpProfileEditorScreen
import com.mineserve.mobile.ui.screens.NetworkScreen
import com.mineserve.mobile.ui.screens.PlayersScreen
import com.mineserve.mobile.ui.screens.PluginsScreen
import com.mineserve.mobile.ui.screens.UpdateDialog
import com.mineserve.mobile.ui.screens.PropertiesScreen
import com.mineserve.mobile.ui.screens.SettingsScreen
import com.mineserve.mobile.ui.screens.ServerManagementScreen
import com.mineserve.mobile.ui.screens.ServerIconScreen
import com.mineserve.mobile.ui.screens.TerminalScreen
import com.mineserve.mobile.ui.screens.EnvManagerScreen
import com.mineserve.mobile.ui.screens.FtpScreen
import com.mineserve.mobile.ui.screens.MoreScreen
import com.mineserve.mobile.ui.screens.AutomationScreen
import com.mineserve.mobile.ui.screens.McpScreen
import com.mineserve.mobile.ui.screens.WidgetSettingsScreen
import com.mineserve.mobile.ui.screens.CrashReportsScreen
import com.mineserve.mobile.ui.screens.CrashReportDialog
import com.mineserve.mobile.ui.screens.WorldImportScreen
import com.mineserve.mobile.ui.screens.WorldsScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack

/** 子页面类型（从设置页进入的二级页面） */
enum class SubPage { Properties, Network, Backup, WorldManage, WorldImport, DownloadHelp, MtGuide, KeepAlive, OpLevelGuide, Diagnostics, Plugins, ServerIcon, CrashReports, WidgetSettings, Mcp, EnvManager, Ftp, FrpProfileEditor, More, Automation, Cores, ServerList, PlayerList, AppSettings, Crossplay, Files, Console, Remote, Performance, WorldRules, PlayerTools, AddonTools }

/**
 * Five primary destinations; secondary tools retain their own back navigation.
 */
@Composable
fun McApp() {
    val vm: McViewModel = viewModel(factory = McViewModel.Factory)
    val config by vm.config.collectAsState()
    val currentCrashContent by vm.currentCrashContent.collectAsState()
    val currentCrashAnalysis by vm.currentCrashAnalysis.collectAsState()
    val lastRunLog by vm.lastRunLog.collectAsState()
    var tab by rememberSaveable { mutableStateOf(McTab.Dashboard) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var subPage by rememberSaveable { mutableStateOf<SubPage?>(null) }
    var worldImportFromTab by rememberSaveable { mutableStateOf(false) }
    var mtGuideFromFiles by rememberSaveable { mutableStateOf(false) }
    val pageState = rememberSaveableStateHolder()

    fun navigateTo(target: SubPage) {
        showLogs = false
        when (target) {
            SubPage.Console -> { subPage = null; tab = McTab.Terminal }
            SubPage.Plugins -> { subPage = null; tab = McTab.Download }
            SubPage.WorldManage -> { subPage = null; tab = McTab.Worlds }
            SubPage.PlayerList -> { subPage = null; tab = McTab.Players }
            SubPage.WorldRules -> { tab = McTab.Worlds; subPage = target }
            SubPage.PlayerTools -> { tab = McTab.Players; subPage = target }
            SubPage.AddonTools -> { tab = McTab.Download; subPage = target }
            SubPage.WorldImport -> {
                worldImportFromTab = tab == McTab.Worlds && subPage == null
                subPage = target
            }
            else -> subPage = target
        }
    }

    fun returnFromWorldImport() {
        subPage = if (worldImportFromTab) null else SubPage.WorldManage
    }
    // 依赖与环境管理页的初始模块（0=Java 1=依赖 2=Termux）
    var envManagerTab by rememberSaveable { mutableStateOf(0) }
    // FRP 配置编辑页要编辑哪一份配置（"" 表示新建）。
    // 单独存一个变量而不是做成带参的 SubPage：SubPage 是 enum，
    // 而编辑目标是个可变 id，两者拆开更清楚。
    var editFrpProfileId by rememberSaveable { mutableStateOf("") }

    // 把 Activity 的前台/后台状态显式推给 VM。
    // 各页面用 collectAsState（非生命周期感知）订阅日志流：Activity 停止后 Compose 组合不销毁、
    // subscriptionCount 恒 > 0，VM 仅凭订阅数无法判断界面是否可见，后台降频会失效。
    LifecycleResumeEffect(Unit) {
        vm.setUiVisible(true)
        onPauseOrDispose { vm.setUiVisible(false) }
    }

    val snackbars = remember { SnackbarHostState() }
    val eulaRequest by vm.eulaRequest.collectAsState()
    val uri = LocalUriHandler.current
    val appContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(vm) { vm.errorFlow.collectLatest { snackbars.showInstantSnackbar(it) } }
    LaunchedEffect(vm) { vm.messageFlow.collectLatest { snackbars.showInstantSnackbar(it) } }
    LaunchedEffect(vm) {
        vm.pendingDriveShareFileFlow.collectLatest { filePath ->
            vm.shareBackupFileToGoogleDrive(appContext, filePath)
        }
    }
    LaunchedEffect(vm) {
        vm.coreDownloadCompletedFlow.collectLatest { newServerName ->
            showLogs = false
            subPage = null
            tab = McTab.Dashboard
            snackbars.showInstantSnackbar("✅ Đã tải xong「$newServerName」! Đã chuyển về mục Khởi động Server.")
        }
    }

    // 系统返回键：逐级返回上一页面，禁止直接退出应用。
    // 优先级：日志页 → 子页面 → 其他 Tab → 概览（Dashboard）；已在概览时消费返回键不退出。
    BackHandler(enabled = showLogs || subPage != null || tab != McTab.Dashboard) {
        when {
            showLogs -> showLogs = false
            // FRP 配置编辑页是从网络页点进来的：返回必须回到网络页，
            // 否则会把用户直接甩回上一个 Tab，丢失「刚才是从哪进来的」这个上下文。
            subPage == SubPage.FrpProfileEditor -> subPage = SubPage.Network
            subPage == SubPage.WorldImport -> returnFromWorldImport()
            subPage == SubPage.MtGuide && mtGuideFromFiles -> subPage = SubPage.Files
            subPage != null -> subPage = null
            tab != McTab.Dashboard -> {
                tab = McTab.Dashboard
                showLogs = false
                subPage = null
            }
        }
    }

    MineHostNavigation(
        title = mineHostPageTitle(tab, subPage, showLogs),
        serverName = config.activeCoreName,
        tab = tab,
        page = subPage,
        onTab = { tab = it; showLogs = false; subPage = null },
        onNavigate = ::navigateTo
    ) {
    Scaffold(
        // 全屏展示：内容延伸到状态栏，顶部状态栏区域由各页面首个组件（HeaderBlock/返回栏）
        // 以白色背景覆盖，不再显示系统状态栏灰色条
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SwipeableInstantSnackbarHost(snackbars) },
        bottomBar = {
            MineHostBottomBar(
                selected = when {
                    subPage == SubPage.WorldRules -> McTab.Worlds
                    subPage == SubPage.PlayerTools -> McTab.Players
                    subPage == SubPage.AddonTools -> McTab.Download
                    subPage != null || showLogs -> null
                    else -> tab
                },
                onSelect = { tab = it; showLogs = false; subPage = null }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            val page = Triple(showLogs, subPage, tab)
            pageState.SaveableStateProvider("${config.activeCoreName}:${page.first}:${page.second}:${page.third}") {
            when {
                page.first -> LogsScreen(vm = vm, onBack = { showLogs = false })
                page.second != null -> {
                    when (page.second) {
                        SubPage.Properties -> PropertiesScreen(vm = vm, onBack = { subPage = null })
                        SubPage.Network -> NetworkScreen(
                            vm = vm,
                            onBack = { subPage = null },
                            onOpenFrpEditor = { id ->
                                editFrpProfileId = id.orEmpty()
                                subPage = SubPage.FrpProfileEditor
                            }
                        )
                        SubPage.FrpProfileEditor -> FrpProfileEditorScreen(
                            vm = vm,
                            profileId = editFrpProfileId.ifBlank { null },
                            onBack = { subPage = SubPage.Network }
                        )
                        SubPage.Backup -> BackupScreen(vm = vm, onBack = { subPage = null })
                        SubPage.WorldManage -> WorldsScreen(
                            vm = vm,
                            onBack = { subPage = null },
                            onImport = { navigateTo(SubPage.WorldImport) },
                            onWorldRules = { navigateTo(SubPage.WorldRules) }
                        )
                        SubPage.WorldImport -> WorldImportScreen(
                            vm = vm,
                            onBack = ::returnFromWorldImport,
                            onWorlds = ::returnFromWorldImport
                        )
                        SubPage.DownloadHelp -> DownloadHelpScreen(vm = vm, onBack = { subPage = null })
                        SubPage.MtGuide -> MtGuideScreen(onBack = {
                            subPage = if (mtGuideFromFiles) SubPage.Files else null
                        })
                        SubPage.KeepAlive -> KeepAliveScreen(vm = vm, onBack = { subPage = null })
                        SubPage.OpLevelGuide -> OpLevelGuideScreen(
                            onBack = { subPage = null },
                            onNavigateProperties = { subPage = SubPage.Properties },
                            onNavigatePlugins = { navigateTo(SubPage.Plugins) }
                        )
                        SubPage.Diagnostics -> DiagnosticsScreen(vm = vm, onBack = { subPage = null })
                        SubPage.Plugins -> PluginsScreen(vm = vm, onAddonTools = { navigateTo(SubPage.AddonTools) })
                        SubPage.ServerIcon -> ServerIconScreen(vm = vm, onBack = { subPage = null })
                        SubPage.CrashReports -> CrashReportsScreen(
                            vm = vm,
                            onBack = { subPage = null },
                            onGoToJavaInstall = {
                                envManagerTab = 0
                                subPage = SubPage.EnvManager
                            },
                            onGoToModManager = {
                                subPage = null
                                tab = McTab.Download
                            },
                            onGoToDiagnostics = {
                                subPage = SubPage.Diagnostics
                            }
                        )
                        SubPage.WidgetSettings -> WidgetSettingsScreen(onBack = { subPage = null })
                        SubPage.EnvManager -> EnvManagerScreen(vm = vm, initialTab = envManagerTab, onBack = { subPage = null })
                        SubPage.Mcp -> McpScreen(vm = vm, onBack = { subPage = null })
                        SubPage.Ftp -> FtpScreen(vm = vm, onBack = { subPage = null })
                        SubPage.Automation -> AutomationScreen(vm = vm, onBack = { subPage = null })
                        SubPage.More -> MoreScreen(
                            onNetwork = { subPage = SubPage.Network },
                            onBackup = { subPage = SubPage.Backup },
                            onDiagnostics = { subPage = SubPage.Diagnostics },
                            onKeepAlive = { subPage = SubPage.KeepAlive },
                            onHelp = { subPage = SubPage.DownloadHelp },
                            onCrashReports = { subPage = SubPage.CrashReports },
                            onWidgetSettings = { subPage = SubPage.WidgetSettings },
                            onMcp = { subPage = SubPage.Mcp },
                            onFtp = { subPage = SubPage.Ftp },
                            onAutomation = { subPage = SubPage.Automation }
                        )
                        SubPage.Cores -> DownloadScreen(vm, onShowDownloadHelp = { subPage = SubPage.DownloadHelp }, onBack = { subPage = null })
                        SubPage.ServerList -> ServerManagementScreen(vm,
                            onPlugins = { subPage = null; tab = McTab.Download },
                            onProperties = { subPage = SubPage.Properties },
                            onIcon = { subPage = SubPage.ServerIcon },
                            onWorlds = { navigateTo(SubPage.WorldManage) },
                            onCreateServer = { subPage = SubPage.Cores },
                            onBack = { subPage = null })
                        SubPage.PlayerList -> PlayersScreen(vm,
                            onNavigateProperties = { subPage = SubPage.Properties },
                            onNavigateOpGuide = { subPage = SubPage.OpLevelGuide },
                        onPlayerTools = { navigateTo(SubPage.PlayerTools) })
                        SubPage.AppSettings -> SettingsScreen(vm, onNavigate = ::navigateTo)
                        SubPage.Crossplay -> MineHostCrossplay(vm = vm, onBack = { subPage = null })
                        SubPage.Files -> ToolSubPage(onBack = { subPage = null }) {
                            FileManagerScreen(vm = vm, onOpenMtGuide = {
                                mtGuideFromFiles = true
                                subPage = SubPage.MtGuide
                            })
                        }
                        SubPage.Console -> ToolSubPage(onBack = { subPage = null }) {
                            TerminalScreen(vm = vm)
                        }
                        SubPage.Remote -> MineHostRemote(vm = vm, onBack = { subPage = null })
                        SubPage.Performance -> MineHostPerformance(
                            vm = vm,
                            onBack = { subPage = null },
                            navigate = ::navigateTo
                        )
                        SubPage.WorldRules, SubPage.PlayerTools, SubPage.AddonTools -> MineHostOverview(
                            vm = vm,
                            navigate = ::navigateTo,
                            onConsole = { navigateTo(SubPage.Console) },
                            section = when (page.second) {
                                SubPage.WorldRules -> 2
                                SubPage.PlayerTools -> 4
                                else -> 5
                            },
                            onBack = { subPage = null }
                        )
                        null -> {}
                    }
                }
                else -> when (page.third) {
                    McTab.Dashboard -> MineHostOverview(vm,
                        navigate = ::navigateTo,
                        onConsole = { navigateTo(SubPage.Console) })
                    McTab.Worlds -> WorldsScreen(
                        vm = vm,
                        onBack = { tab = McTab.Dashboard },
                        onImport = { navigateTo(SubPage.WorldImport) },
                            onWorldRules = { navigateTo(SubPage.WorldRules) }
                    )
                    McTab.Download -> PluginsScreen(vm = vm, onAddonTools = { navigateTo(SubPage.AddonTools) })
                    McTab.ServerManagement -> ServerManagementScreen(
                        vm = vm,
                        onPlugins = { subPage = null; tab = McTab.Download },
                        onProperties = { subPage = SubPage.Properties },
                        onIcon = { subPage = SubPage.ServerIcon },
                        onWorlds = { navigateTo(SubPage.WorldManage) },
                        onCreateServer = { subPage = SubPage.Cores }
                    )
                    McTab.Players -> PlayersScreen(
                        vm = vm,
                        onNavigateProperties = { subPage = SubPage.Properties },
                        onNavigateOpGuide = { subPage = SubPage.OpLevelGuide },
                        onPlayerTools = { navigateTo(SubPage.PlayerTools) }
                    )
                    McTab.Files -> FileManagerScreen(vm = vm, onOpenMtGuide = {
                        mtGuideFromFiles = false
                        subPage = SubPage.MtGuide
                    })
                    McTab.Terminal -> TerminalScreen(vm = vm)
                    McTab.Settings -> MineHostManage(vm = vm, navigate = ::navigateTo)
                }
            }
            }
        }

        // 软件更新对话框（全局，覆盖所有页面）
        UpdateDialog(vm = vm)
        eulaRequest?.let { requestedDir ->
            AlertDialog(
                onDismissRequest = vm::dismissEula,
                title = { Text("Minecraft EULA") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text("Bạn cần đọc và đồng ý Minecraft EULA để chạy server này. MineHost không tự đồng ý thay bạn.")
                        TextButton(onClick = { uri.openUri("https://www.minecraft.net/eula") }) { Text("Đọc EULA chính thức") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.acceptEulaAndStart(requestedDir) }) { Text("Tôi đồng ý và bật server") }
                },
                dismissButton = { TextButton(onClick = vm::dismissEula) { Text("Chưa đồng ý") } }
            )
        }
        val crashContent = currentCrashContent
        val crashAnalysis = currentCrashAnalysis
        if (crashContent != null && crashAnalysis != null) {
            CrashReportDialog(
                crashContent,
                crashAnalysis,
                lastRunLog,
                onDismiss = { vm.clearCrashContent() },
                vm = vm,
                onGoToJavaInstall = {
                    vm.clearCrashContent()
                    envManagerTab = 0
                    subPage = SubPage.EnvManager
                },
                onGoToModManager = {
                    vm.clearCrashContent()
                    subPage = null
                    tab = McTab.Download
                },
                onGoToDiagnostics = {
                    vm.clearCrashContent()
                    subPage = SubPage.Diagnostics
                },
                onGoToProperties = {
                    vm.clearCrashContent()
                    subPage = SubPage.Properties
                }
            )
        }
    }
    }
}

@Composable
private fun ToolSubPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            Text("Quay lại", modifier = Modifier.padding(start = 8.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

