package com.mineserve.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mineserve.mobile.ui.theme.AppUiSettings
import com.mineserve.mobile.ui.theme.EyeCareColorPreset
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import com.mineserve.mobile.ui.theme.horizonGlassTokensFor
import kotlin.math.abs
import kotlin.math.roundToInt

/** Lets page headers yield their large title to the shared app bar. */
val LocalMineHostChrome = staticCompositionLocalOf { false }

internal val mineHostMainTabs = listOf(
    McTab.Dashboard to "Máy chủ",
    McTab.Terminal to "Console",
    McTab.Worlds to "Thế giới",
    McTab.Players to "Người chơi",
    McTab.Download to "Mod"
)

@Composable
internal fun MineHostBottomBar(selected: McTab?, onSelect: (McTab) -> Unit) {
    val largeLabels = LocalDensity.current.fontScale > 1.3f
    val glass = HorizonGlass
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(glass.glassChrome)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(glass.hairline, glass.specularTop, glass.hairline)
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .height(IntrinsicSize.Min)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            mineHostMainTabs.forEach { (destination, label) ->
                val active = selected == destination
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(active, role = Role.Tab, onClick = { onSelect(destination) })
                        .semantics { contentDescription = label }
                        .heightIn(min = 72.dp)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 28.dp)
                            .then(
                                if (active) {
                                    Modifier.horizonGlassPlate(
                                        shape = McShape.Pill,
                                        containerColor = glass.accentSoft,
                                        borderColor = glass.accent,
                                        highlighted = true
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (largeLabels && destination == McTab.Terminal) "Lệnh" else label,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

internal fun mineHostPageTitle(tab: McTab, page: SubPage?, logs: Boolean): String = when {
    logs -> "Nhật ký server"
    page != null -> when (page) {
        SubPage.Properties -> "Cấu hình server"
        SubPage.Network, SubPage.FrpProfileEditor -> "Mạng nâng cao"
        SubPage.Backup -> "Cài đặt sao lưu"
        SubPage.WorldManage -> "Thế giới"
        SubPage.WorldImport -> "Nhập thế giới"
        SubPage.WorldRules -> "Luật chơi & tọa độ"
        SubPage.PlayerTools -> "Tiện ích người chơi"
        SubPage.AddonTools -> "Gói mở rộng"
        SubPage.DownloadHelp, SubPage.MtGuide, SubPage.OpLevelGuide -> "Hướng dẫn"
        SubPage.KeepAlive -> "Chạy nền"
        SubPage.Diagnostics -> "Chẩn đoán"
        SubPage.Plugins -> "Mod & Plugin"
        SubPage.ServerIcon -> "Tên & biểu tượng server"
        SubPage.CrashReports -> "Báo cáo lỗi"
        SubPage.WidgetSettings -> "Tiện ích màn hình chính"
        SubPage.Mcp -> "Kết nối MCP"
        SubPage.EnvManager -> "Môi trường Java"
        SubPage.Ftp -> "Truy cập FTP"
        SubPage.More -> "Công cụ khác"
        SubPage.Automation -> "Lịch tự động"
        SubPage.Cores -> "Tạo máy chủ"
        SubPage.ServerList -> "Danh sách máy chủ"
        SubPage.PlayerList -> "Người chơi"
        SubPage.AppSettings -> "Cài đặt nâng cao"
        SubPage.Crossplay -> "Chơi chung Java & PE"
        SubPage.Files -> "Quản lý tệp"
        SubPage.Console -> "Console"
        SubPage.Remote -> "Điều khiển từ xa"
        SubPage.Performance -> "Tối ưu & chạy nền"
    }
    else -> when (tab) {
        McTab.Dashboard -> "Máy chủ"
        McTab.Terminal -> "Console"
        McTab.Worlds -> "Thế giới"
        McTab.Players -> "Người chơi"
        McTab.Download -> "Mod & Plugin"
        McTab.Files -> "Quản lý tệp"
        McTab.ServerManagement -> "Danh sách máy chủ"
        McTab.Settings -> "Công cụ & ứng dụng"
    }
}

@Composable
internal fun MineHostNavigation(
    title: String,
    serverName: String?,
    tab: McTab,
    page: SubPage?,
    onTab: (McTab) -> Unit,
    onNavigate: (SubPage) -> Unit,
    onCleanStorage: (() -> Unit)? = null,
    liveStatusPillText: String? = null,
    liveStatusState: Int = 0, // 0 = offline, 1 = starting, 2 = online, 3 = warning
    onLiveStatusClick: (() -> Unit)? = null,
    drawerStatusSummary: String? = null,
    onQuickTelegramBackup: (() -> Unit)? = null,
    enableQuickSearch: Boolean = false,
    content: @Composable () -> Unit
) {
    val glass = HorizonGlass
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var themeStudioOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val menuVisibility = remember { MutableTransitionState(false) }

    LaunchedEffect(menuOpen) {
        menuVisibility.targetState = menuOpen
        if (!menuOpen) searchQuery = ""
    }

    fun choose(action: () -> Unit) {
        menuOpen = false
        action()
    }

    CompositionLocalProvider(LocalMineHostChrome provides true) {
        Column(Modifier.fillMaxSize()) {
            // Thanh đỉnh kính mờ Pure Horizon Glass (trải liền mạch dưới thanh trạng thái)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(glass.glassChrome)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .heightIn(min = 58.dp)
                        .padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!liveStatusPillText.isNullOrBlank()) {
                            val (pillBg, pillFg, pillBorder) = when (liveStatusState) {
                                2 -> Triple(glass.liveGreenSoft, glass.liveGreen, glass.liveGreen.copy(alpha = 0.48f))
                                1 -> Triple(glass.skyCyan.copy(alpha = 0.16f), glass.skyCyan, glass.skyCyan.copy(alpha = 0.48f))
                                3 -> Triple(glass.warnAmberSoft, glass.warnAmber, glass.warnAmber.copy(alpha = 0.50f))
                                else -> Triple(glass.glassRow, MaterialTheme.colorScheme.onSurfaceVariant, glass.hairline)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(McShape.Pill)
                                    .background(pillBg, McShape.Pill)
                                    .border(1.dp, pillBorder, McShape.Pill)
                                    .then(
                                        if (onLiveStatusClick != null) {
                                            Modifier.clickable(role = Role.Button, onClick = onLiveStatusClick)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = liveStatusPillText,
                                    style = MaterialTheme.typography.labelMedium.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.SemiBold,
                                    color = pillFg
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { themeStudioOpen = true },
                        modifier = Modifier
                            .size(44.dp)
                            .horizonGlassPlate(
                                shape = McShape.Pill,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = "Chủ đề giao diện",
                            tint = glass.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .horizonGlassPlate(
                                shape = McShape.Pill,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "Mở danh mục",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(glass.hairline)
                )
            }

            Box(Modifier.weight(1f)) { content() }
        }
    }

    if (themeStudioOpen) {
        HorizonThemeStudioDialog(onDismiss = { themeStudioOpen = false })
    }

    // Bảng danh mục trượt cạnh phải bằng kính mờ Pure Horizon Glass
    if (menuVisibility.currentState || menuVisibility.targetState) {
        Dialog(
            onDismissRequest = { menuOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val panelWidth = minOf(maxWidth * 0.92f, 380.dp)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (glass.isLight) 0.34f else 0.56f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Đóng danh mục"
                        ) { menuOpen = false }
                )
                AnimatedVisibility(
                    visibleState = menuVisibility,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(tween(220)) { it },
                    exit = slideOutHorizontally(tween(180)) { it }
                ) {
                    val drawerShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                    Surface(
                        onClick = {},
                        modifier = Modifier
                            .width(panelWidth)
                            .fillMaxHeight()
                            .semantics { paneTitle = "Danh mục" },
                        color = Color.Transparent,
                        shape = drawerShape
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizonGlassPlate(
                                    shape = drawerShape,
                                    containerColor = glass.glassChrome,
                                    borderColor = glass.specularTop
                                )
                                .safeDrawingPadding()
                        ) {
                            val q = searchQuery.trim().lowercase()
                            fun matchesSearch(label: String, detail: String): Boolean =
                                q.isBlank() || label.lowercase().contains(q) || detail.lowercase().contains(q)

                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 24.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Danh mục",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { menuOpen = false },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Đóng danh mục",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .fillMaxWidth()
                                        .horizonGlassPlate(
                                            shape = McShape.Row,
                                            containerColor = glass.glassRow,
                                            borderColor = glass.hairline
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = serverName ?: "Chưa chọn máy chủ",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (drawerStatusSummary != null) {
                                            Text(
                                                text = drawerStatusSummary,
                                                style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                if (enableQuickSearch) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        placeholder = { Text("🔍 Tìm nhanh công cụ (skin, lag, tele...)") },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { searchQuery = "" }) {
                                                    Icon(Icons.Outlined.Clear, contentDescription = "Xóa tìm kiếm")
                                                }
                                            }
                                        },
                                        shape = McShape.Row,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = glass.glassInput,
                                            unfocusedContainerColor = glass.glassInput,
                                            focusedBorderColor = glass.accent,
                                            unfocusedBorderColor = glass.hairline
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }

                                if (q.isBlank() && (onQuickTelegramBackup != null || onCleanStorage != null)) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            if (onQuickTelegramBackup != null) {
                                                OutlinedButton(
                                                    onClick = { choose { onQuickTelegramBackup() } },
                                                    shape = McShape.ControlCompact,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("☁️ Lưu Map Tele", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                            if (onCleanStorage != null) {
                                                OutlinedButton(
                                                    onClick = { choose { onCleanStorage() } },
                                                    shape = McShape.ControlCompact,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("🧹 Dọn Rác", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { choose { onTab(McTab.Worlds) } },
                                                shape = McShape.ControlCompact,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("🗺️ Đổi Map", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            }
                                            OutlinedButton(
                                                onClick = { choose { onNavigate(SubPage.Remote) } },
                                                shape = McShape.ControlCompact,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("✈️ Bot Telegram", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }

                                val quickTabsFiltered = mineHostMainTabs.map { (destination, label) ->
                                    val detail = when (destination) {
                                        McTab.Dashboard -> "Bật / dừng, IP và trò chuyện"
                                        McTab.Terminal -> "Lệnh và nhật ký trực tiếp"
                                        McTab.Worlds -> "Map, pack sinh tồn và sao lưu"
                                        McTab.Players -> "Online, OP và túi đồ"
                                        else -> "Kho tải và các gói đã cài"
                                    }
                                    Triple(destination, label, detail)
                                }.filter { (_, label, detail) -> matchesSearch(label, detail) }

                                if (quickTabsFiltered.isNotEmpty()) {
                                    DrawerGroup("Truy cập nhanh")
                                    quickTabsFiltered.forEach { (destination, label, detail) ->
                                        DrawerLink(label, detail, destination.icon, page == null && tab == destination) {
                                            choose { onTab(destination) }
                                        }
                                    }
                                }

                                val manageItems = listOf(
                                    Triple(SubPage.ServerList, "Danh sách máy chủ", "Chọn, tạo và quản lý server"),
                                    Triple(SubPage.Crossplay, "Chơi chung PC & PE", "Cài GeyserMC cho điện thoại"),
                                    Triple(SubPage.WorldRules, "Luật chơi & tọa độ", "Giữ đồ, Scoreboard, BossBar và warp"),
                                    Triple(SubPage.PlayerTools, "Tiện ích người chơi", "Skin, danh hiệu và bảo vệ OP"),
                                    Triple(SubPage.AddonTools, "Gói mở rộng", "Addon, voice chat và datapack"),
                                    Triple(SubPage.Files, "Tệp & thư mục", "World, mod, plugin và cấu hình"),
                                    Triple(SubPage.Properties, "Cấu hình server", "server.properties và thiết lập chơi"),
                                    Triple(SubPage.Performance, "Tối ưu & chạy nền", "RAM, Radar dọn lag, pin và nhiệt độ"),
                                    Triple(SubPage.Automation, "Lịch tự động", "Sao lưu và khởi động theo lịch")
                                ).filter { (_, label, detail) -> matchesSearch(label, detail) }

                                if (manageItems.isNotEmpty()) {
                                    DrawerGroup("Vận hành & quản lý")
                                    manageItems.forEach { (target, label, detail) ->
                                        DrawerLink(
                                            label,
                                            detail,
                                            when (target) {
                                                SubPage.Files -> Icons.Outlined.Folder
                                                SubPage.ServerList -> Icons.Outlined.Dns
                                                SubPage.Crossplay -> Icons.Outlined.Devices
                                                SubPage.Performance -> Icons.Outlined.Tune
                                                SubPage.WorldRules -> Icons.Outlined.Public
                                                SubPage.PlayerTools -> Icons.Outlined.People
                                                SubPage.AddonTools -> Icons.Outlined.Extension
                                                SubPage.Automation -> Icons.Outlined.Schedule
                                                else -> Icons.Outlined.Settings
                                            },
                                            page == target
                                        ) { choose { onNavigate(target) } }
                                    }
                                }

                                val appItems = listOf(
                                    Triple(SubPage.Remote, "Điều khiển từ xa", "Telegram Cloud, Discord và Web PIN"),
                                    Triple(SubPage.Network, "Mạng nâng cao", "FRP, Bore và cấu hình đường hầm"),
                                    Triple(SubPage.EnvManager, "Môi trường Java", "Phiên bản Java và môi trường chạy"),
                                    Triple(SubPage.Diagnostics, "Chẩn đoán", "Kiểm tra và hướng dẫn sửa lỗi")
                                ).filter { (_, label, detail) -> matchesSearch(label, detail) }

                                val showClean = onCleanStorage != null && matchesSearch("Dọn rác bộ nhớ", "Xóa tệp tạm, log cũ và cache")
                                val showAppSettings = matchesSearch("Công cụ & ứng dụng", "Giao diện, Google Drive và cập nhật")

                                if (appItems.isNotEmpty() || showClean || showAppSettings) {
                                    DrawerGroup("Kết nối & ứng dụng")
                                    appItems.forEach { (target, label, detail) ->
                                        DrawerLink(
                                            label,
                                            detail,
                                            when (target) {
                                                SubPage.Remote -> Icons.Outlined.Cloud
                                                SubPage.Network -> Icons.Outlined.Public
                                                SubPage.EnvManager -> Icons.Outlined.Memory
                                                else -> Icons.Outlined.Build
                                            },
                                            page == target
                                        ) { choose { onNavigate(target) } }
                                    }
                                    if (showClean) {
                                        DrawerLink("Dọn rác bộ nhớ", "Xóa tệp tạm, log cũ và cache", Icons.Outlined.DeleteSweep, false) {
                                            choose { onCleanStorage?.invoke() }
                                        }
                                    }
                                    if (showAppSettings) {
                                        DrawerLink("Công cụ & ứng dụng", "Giao diện, Google Drive và cập nhật", Icons.Outlined.Settings, page == null && tab == McTab.Settings) {
                                            choose { onTab(McTab.Settings) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizonThemeStudioDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val glass = HorizonGlass
    val currentPreset by AppUiSettings.colorPreset.collectAsState()
    val currentFontScale by AppUiSettings.fontScale.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 400.dp)
                .horizonGlassPlate(
                    shape = McShape.Dialog,
                    containerColor = glass.glassChrome,
                    borderColor = glass.specularTop
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "🎨 Chủ đề Horizon Glass",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Chọn không gian kính mờ & cỡ chữ tức thì",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Đóng bảng chủ đề",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EyeCareColorPreset.entries.forEach { preset ->
                        val active = preset == currentPreset
                        val pGlass = remember(preset) { horizonGlassTokensFor(preset) }
                        val desc = when (preset) {
                            EyeCareColorPreset.SoftSlate -> "Kính tối ánh tím cực quang mặc định"
                            EyeCareColorPreset.BlockWayDark -> "Kính xanh đại dương ban đêm dịu mắt"
                            EyeCareColorPreset.WarmMocha -> "Kính hổ phách ấm giảm ánh sáng xanh"
                            EyeCareColorPreset.SakuraGlass -> "Kính sáng hoa anh đào trong trẻo"
                            EyeCareColorPreset.WarmPaperLight -> "Kính sáng băng tinh khiết ngoài trời"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = if (active) glass.accentSoft else glass.glassRow,
                                    borderColor = if (active) glass.accent else glass.hairline,
                                    highlighted = active
                                )
                                .clickable(role = Role.RadioButton) {
                                    AppUiSettings.setColorPreset(context, preset)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(pGlass.skyTop, pGlass.horizonGlow, pGlass.accent)
                                        )
                                    )
                                    .border(1.dp, pGlass.specularTop, CircleShape)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = preset.labelVi,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (active) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = glass.hairline)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cỡ chữ hiển thị",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(currentFontScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = currentFontScale,
                        onValueChange = { AppUiSettings.setFontScale(context, it) },
                        valueRange = 0.90f..1.25f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.92f to "92%", 1.00f to "100%", 1.08f to "108%", 1.20f to "120%").forEach { (scale, text) ->
                            val isNear = abs(currentFontScale - scale) < 0.03f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp)
                                    .horizonGlassPlate(
                                        shape = McShape.ControlCompact,
                                        containerColor = if (isNear) glass.accentSoft else glass.glassRow,
                                        borderColor = if (isNear) glass.accent else glass.hairline,
                                        highlighted = isNear
                                    )
                                    .clickable { AppUiSettings.setFontScale(context, scale) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelMedium.merge(HorizonTabularStyle),
                                    color = if (isNear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isNear) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = McShape.Control,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Hoàn tất", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DrawerGroup(title: String) {
    HorizontalDivider(
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 20.dp, end = 20.dp),
        color = HorizonGlass.hairline
    )
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerLink(
    title: String,
    detail: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val glass = HorizonGlass
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = McShape.Row,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.horizonGlassPlate(
                        shape = McShape.Row,
                        containerColor = glass.accentSoft,
                        borderColor = glass.accent,
                        highlighted = true
                    )
                } else {
                    Modifier.clip(McShape.Row)
                }
            )
            .semantics { this.selected = selected }
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else glass.glassRow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
