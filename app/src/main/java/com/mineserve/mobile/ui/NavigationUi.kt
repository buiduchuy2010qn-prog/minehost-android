package com.mineserve.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mineserve.mobile.ui.theme.LiveStatusDot
import com.mineserve.mobile.ui.theme.McCodeStyle
import com.mineserve.mobile.ui.theme.McMotion
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.mineHostColors

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
    val haptic = LocalHapticFeedback.current
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                    .height(IntrinsicSize.Min)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                mineHostMainTabs.forEach { (destination, label) ->
                    val active = selected == destination
                    val indicatorColor by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(McMotion.FastMs),
                        label = "nav_pill_bg"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(McMotion.FastMs),
                        label = "nav_icon_tint"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(McMotion.FastMs),
                        label = "nav_label_tint"
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = active,
                                role = Role.Tab,
                                onClick = {
                                    if (!active) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(destination)
                                }
                            )
                            .semantics { contentDescription = label }
                            .heightIn(min = 68.dp)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 30.dp)
                                .clip(CircleShape)
                                .background(indicatorColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (active) destination.selectedIcon else destination.icon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = iconTint
                            )
                        }
                        val displayLabel = if (largeLabels) {
                            when (destination) {
                                McTab.Dashboard -> "Chính"
                                McTab.Terminal -> "Lệnh"
                                McTab.Worlds -> "Map"
                                McTab.Players -> "Khách"
                                else -> "Mod"
                            }
                        } else {
                            label
                        }
                        Text(
                            displayLabel,
                            modifier = Modifier.fillMaxWidth(),
                            style = if (largeLabels) {
                                MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, lineHeight = 13.sp)
                            } else {
                                MaterialTheme.typography.labelSmall
                            },
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = labelColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
    val semanticColors = MaterialTheme.mineHostColors
    var menuOpen by rememberSaveable { mutableStateOf(false) }
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
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp)
                    )
                    if (!liveStatusPillText.isNullOrBlank()) {
                        val (pillBg, pillFg, pulsing) = when (liveStatusState) {
                            2 -> Triple(semanticColors.successContainer, semanticColors.success, true)
                            1 -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, true)
                            3 -> Triple(semanticColors.warningContainer, semanticColors.warning, true)
                            else -> Triple(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurfaceVariant, false)
                        }
                        Surface(
                            onClick = { onLiveStatusClick?.invoke() },
                            enabled = onLiveStatusClick != null,
                            shape = CircleShape,
                            color = pillBg,
                            border = BorderStroke(
                                1.dp,
                                if (liveStatusState == 0) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                                else pillFg.copy(alpha = 0.42f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                LiveStatusDot(color = pillFg, active = pulsing, size = 6.dp)
                                Text(
                                    liveStatusPillText,
                                    style = McCodeStyle.copy(
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        lineHeight = MaterialTheme.typography.labelMedium.lineHeight
                                    ),
                                    color = pillFg,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    ) {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                Icons.Rounded.Menu,
                                contentDescription = "Mở danh mục",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
    // Native modal panel for end-side navigation without shifting layout direction.
    if (menuVisibility.currentState || menuVisibility.targetState) {
        Dialog(
            onDismissRequest = { menuOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val panelWidth = minOf(maxWidth * 0.90f, 372.dp)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Đóng danh mục"
                        ) { menuOpen = false }
                )
                AnimatedVisibility(
                    visibleState = menuVisibility,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(tween(McMotion.NormalMs, easing = McMotion.StandardEasing)) { it },
                    exit = slideOutHorizontally(tween(McMotion.FastMs, easing = McMotion.StandardEasing)) { it }
                ) {
                    Surface(
                        onClick = {},
                        modifier = Modifier
                            .width(panelWidth)
                            .fillMaxHeight()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                            )
                            .semantics { paneTitle = "Danh mục" },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                    ) {
                        Box(Modifier.safeDrawingPadding()) {
                            val q = searchQuery.trim().lowercase()
                            fun matchesSearch(label: String, detail: String): Boolean =
                                q.isBlank() || label.lowercase().contains(q) || detail.lowercase().contains(q)

                            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Danh mục",
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { menuOpen = false }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Đóng danh mục")
                                    }
                                }
                                if (drawerStatusSummary != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            LiveStatusDot(
                                                color = if (liveStatusState == 2) semanticColors.success else MaterialTheme.colorScheme.primary,
                                                active = liveStatusState == 2,
                                                size = 7.dp
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    serverName ?: "Chưa chọn máy chủ",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    drawerStatusSummary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        serverName ?: "Chưa chọn máy chủ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                    )
                                }

                                if (enableQuickSearch) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        placeholder = { Text("Tìm nhanh công cụ...", style = MaterialTheme.typography.bodyMedium) },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { searchQuery = "" }) {
                                                    Icon(Icons.Rounded.Clear, contentDescription = "Xóa tìm kiếm")
                                                }
                                            }
                                        },
                                        shape = McShape.Control,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }

                                if (q.isBlank() && (onQuickTelegramBackup != null || onCleanStorage != null)) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            if (onQuickTelegramBackup != null) {
                                                DrawerQuickButton(
                                                    label = "Lưu Tele",
                                                    icon = Icons.Rounded.CloudUpload,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { choose { onQuickTelegramBackup() } }
                                                )
                                            }
                                            if (onCleanStorage != null) {
                                                DrawerQuickButton(
                                                    label = "Dọn rác",
                                                    icon = Icons.Rounded.CleaningServices,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { choose { onCleanStorage() } }
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            DrawerQuickButton(
                                                label = "Đổi Map",
                                                icon = Icons.Rounded.Public,
                                                modifier = Modifier.weight(1f),
                                                onClick = { choose { onTab(McTab.Worlds) } }
                                            )
                                            DrawerQuickButton(
                                                label = "Bot Tele",
                                                icon = Icons.Rounded.Send,
                                                modifier = Modifier.weight(1f),
                                                onClick = { choose { onNavigate(SubPage.Remote) } }
                                            )
                                        }
                                    }
                                }

                                val quickTabsFiltered = mineHostMainTabs.map { (destination, label) ->
                                    val detail = when (destination) {
                                        McTab.Dashboard -> "Bật / dừng, IP và mời bạn"
                                        McTab.Terminal -> "Lệnh và nhật ký trực tiếp"
                                        McTab.Worlds -> "Map, pack sinh tồn và sao lưu"
                                        McTab.Players -> "Online, OP và túi đồ"
                                        else -> "Kho tải và gói đã cài"
                                    }
                                    Triple(destination, label, detail)
                                }.filter { (_, label, detail) -> matchesSearch(label, detail) }

                                if (quickTabsFiltered.isNotEmpty()) {
                                    DrawerGroup("Điều khiển chính")
                                    quickTabsFiltered.forEach { (destination, label, detail) ->
                                        DrawerLink(label, detail, destination.selectedIcon, page == null && tab == destination) {
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
                                                SubPage.Files -> Icons.Rounded.Folder
                                                SubPage.ServerList -> Icons.Rounded.Dns
                                                SubPage.Crossplay -> Icons.Rounded.Devices
                                                SubPage.Performance -> Icons.Rounded.Tune
                                                SubPage.WorldRules -> Icons.Rounded.Public
                                                SubPage.PlayerTools -> Icons.Rounded.People
                                                SubPage.AddonTools -> Icons.Rounded.Extension
                                                SubPage.Automation -> Icons.Rounded.Schedule
                                                else -> Icons.Rounded.Settings
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
                                                SubPage.Remote -> Icons.Rounded.Cloud
                                                SubPage.Network -> Icons.Rounded.Public
                                                SubPage.EnvManager -> Icons.Rounded.Memory
                                                else -> Icons.Rounded.Build
                                            },
                                            page == target
                                        ) { choose { onNavigate(target) } }
                                    }
                                    if (showClean) {
                                        DrawerLink("Dọn rác bộ nhớ", "Xóa tệp tạm, log cũ và cache", Icons.Rounded.DeleteSweep, false) {
                                            choose { onCleanStorage?.invoke() }
                                        }
                                    }
                                    if (showAppSettings) {
                                        DrawerLink("Công cụ & ứng dụng", "Giao diện, Google Drive và cập nhật", Icons.Rounded.Settings, page == null && tab == McTab.Settings) {
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
private fun DrawerQuickButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerGroup(title: String) {
    HorizontalDivider(
        Modifier.padding(top = 12.dp, bottom = 6.dp, start = 20.dp, end = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerLink(title: String, detail: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = McShape.Control,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth()
            .semantics { this.selected = selected }
    ) {
        Row(
            Modifier.heightIn(min = 58.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
