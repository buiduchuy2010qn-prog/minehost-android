package com.mineserve.mobile.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        mineHostMainTabs.forEach { (destination, label) ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
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
    content: @Composable () -> Unit
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val contentDirection = LocalLayoutDirection.current
    fun choose(action: () -> Unit) {
        action()
        scope.launch { drawer.close() }
    }
    // Mirror only the drawer placement; all product text and content retain LTR.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = drawer.isOpen,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
                    ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.90f).widthIn(max = 380.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Danh mục", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { scope.launch { drawer.close() } }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Đóng danh mục")
                                }
                            }
                            Text(serverName ?: "Chưa chọn máy chủ", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                            DrawerGroup("Truy cập nhanh")
                            mineHostMainTabs.forEach { (destination, label) ->
                                DrawerLink(label, when (destination) {
                                    McTab.Dashboard -> "Bật / dừng, IP và trò chuyện"
                                    McTab.Terminal -> "Lệnh và nhật ký trực tiếp"
                                    McTab.Worlds -> "Map, nhập và bản sao lưu"
                                    McTab.Players -> "Online, OP và túi đồ"
                                    else -> "Kho tải và các gói đã cài"
                                }, destination.icon, page == null && tab == destination) { choose { onTab(destination) } }
                            }
                            DrawerGroup("Vận hành & quản lý")
                            listOf(
                                Triple(SubPage.ServerList, "Danh sách máy chủ", "Chọn, tạo và quản lý server"),
                                Triple(SubPage.Files, "Tệp & thư mục", "World, mod, plugin và cấu hình"),
                                Triple(SubPage.Properties, "Cấu hình server", "server.properties và thiết lập chơi"),
                                Triple(SubPage.Performance, "Tối ưu & chạy nền", "RAM, chống lag, pin và nhiệt độ"),
                                Triple(SubPage.WorldRules, "Luật chơi & tọa độ", "Giữ đồ, thời tiết, biên giới và warp"),
                                Triple(SubPage.PlayerTools, "Tiện ích người chơi", "Skin, danh hiệu và bảo vệ OP"),
                                Triple(SubPage.AddonTools, "Gói mở rộng", "Addon, voice chat và datapack"),
                                Triple(SubPage.Automation, "Lịch tự động", "Sao lưu và khởi động theo lịch")
                            ).forEach { (target, label, detail) ->
                                DrawerLink(label, detail, when (target) {
                                    SubPage.Files -> Icons.Outlined.Folder
                                    SubPage.ServerList -> Icons.Outlined.Dns
                                    SubPage.Performance -> Icons.Outlined.Tune
                                    SubPage.WorldRules -> Icons.Outlined.Public
                                    SubPage.PlayerTools -> Icons.Outlined.People
                                    SubPage.AddonTools -> Icons.Outlined.Extension
                                    SubPage.Automation -> Icons.Outlined.Schedule
                                    else -> Icons.Outlined.Settings
                                }, page == target) { choose { onNavigate(target) } }
                            }
                            DrawerGroup("Kết nối & ứng dụng")
                            DrawerLink("Điều khiển từ xa", "Telegram, Discord và Web nội bộ", Icons.Outlined.Cloud, page == SubPage.Remote) { choose { onNavigate(SubPage.Remote) } }
                            DrawerLink("Mạng nâng cao", "FRP, Bore và cấu hình đường hầm", Icons.Outlined.Public, page == SubPage.Network) { choose { onNavigate(SubPage.Network) } }
                            DrawerLink("Môi trường Java", "Phiên bản Java và môi trường chạy", Icons.Outlined.Memory, page == SubPage.EnvManager) { choose { onNavigate(SubPage.EnvManager) } }
                            DrawerLink("Chẩn đoán", "Kiểm tra và hướng dẫn sửa lỗi", Icons.Outlined.Build, page == SubPage.Diagnostics) { choose { onNavigate(SubPage.Diagnostics) } }
                            DrawerLink("Công cụ & ứng dụng", "Giao diện, Google Drive và cập nhật", Icons.Outlined.Settings, page == null && tab == McTab.Settings) { choose { onTab(McTab.Settings) } }
                        }
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides contentDirection, LocalMineHostChrome provides true) {
                Column(Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                            IconButton(onClick = { scope.launch { drawer.open() } }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Mở danh mục")
                            }
                        }
                    }
                    Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) { content() }
                }
                BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
            }
        }
    }
}

@Composable
private fun DrawerGroup(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp, start = 20.dp, end = 20.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun DrawerLink(title: String, detail: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth().semantics { this.selected = selected }
    ) {
        Row(Modifier.heightIn(min = 64.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
