@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.mineserve.mobile.ui.screens

import androidx.compose.ui.res.stringResource
import com.mineserve.mobile.R

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.server.PluginManager
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.EmptyHint
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegmentedTabs
import com.mineserve.mobile.ui.SwipeableInstantSnackbarHost
import com.mineserve.mobile.ui.showInstantSnackbar
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.CoralSoft
import com.mineserve.mobile.ui.theme.FieldGray
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.IndigoSoft
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.MintSoft
import com.mineserve.mobile.ui.theme.Muted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class PluginTab(val labelRes: Int) { Installed(R.string.s735), Upload(R.string.s736) }

/** 4 Phân mục Tài nguyên: Plugin / Mod / Gói Mod & Nhập ZIP / Gói Tài Nguyên */
private enum class ResourceType(val titleVi: String) {
    Plugin("🔌 Plugin"),
    Mod("🧩 Mod"),
    Modpack("📦 Gói Mod & ZIP"),
    ResourcePack("🎨 Gói Tài Nguyên")
}

/** 插件资源站点（点击跳转官网） */
private data class PluginSite(val name: String, val descRes: Int, val url: String)

private val pluginSites = listOf(
    PluginSite(
        "SpigotMC Resources",
        R.string.s739,
        "https://www.spigotmc.org/resources/"
    ),
    PluginSite(
        "Hangar (Paper chính thức)",
        R.string.s741,
        "https://hangar.papermc.io/"
    ),
    PluginSite(
        "Modrinth",
        R.string.s742,
        "https://modrinth.com/plugins"
    ),
    PluginSite(
        "BuiltByBit (MC-Market)",
        R.string.s744,
        "https://builtbybit.com"
    ),
    PluginSite(
        "CurseForge Bukkit",
        R.string.s746,
        "https://www.curseforge.com/minecraft/bukkit-plugins"
    )
)
private enum class InstalledFilter(val labelRes: Int) { All(R.string.s747), Enabled(R.string.s748), Disabled(R.string.s749), Local(R.string.s127) }

/**
 * 插件管理页（重构完善版）
 *
 * 完善功能：
 *  - P0-1 plugin.yml 元信息解析（真实名称/版本/作者/依赖）
 *  - P0-2 GitHub API 更新检测（5 分钟缓存）
 *  - P0-3 插件详情对话框（完整元信息）
 *  - P1-4 搜索 + 筛选标签
 *  - P1-5 自定义 URL 下载
 *  - P1-6 删除时询问清理数据目录
 */
@Composable
fun PluginsScreen(vm: McViewModel, onAddonTools: () -> Unit = {}) {
    // Nạp mô tả tiếng Việt + Server/Client của danh mục 56 mod để mod ĐÃ CÀI cũng hiện đúng thông tin
    remember {
        vm.userScreenshot56ModItems().forEach {
            AddonInfoVi.registerCatalog(it.slug, it.originalJarName, it.title, it.scopeVi, it.descVi)
        }
        true
    }
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val isBootstrapped by vm.isBootstrapped.collectAsStateWithLifecycle()
    val installedPlugins by vm.installedPlugins.collectAsStateWithLifecycle()
    val mods by vm.mods.collectAsStateWithLifecycle()
    val modrinthResults by vm.modrinthResults.collectAsStateWithLifecycle()
    val modrinthLoaders by vm.modrinthLoaders.collectAsStateWithLifecycle()
    val modrinthGameVersions by vm.modrinthGameVersions.collectAsStateWithLifecycle()
    val selectedModVersion by vm.selectedModVersion.collectAsStateWithLifecycle()
    val pluginModrinthResults by vm.pluginModrinthResults.collectAsStateWithLifecycle()
    val pluginModrinthLoaders by vm.pluginModrinthLoaders.collectAsStateWithLifecycle()
    val selectedPluginVersion by vm.selectedPluginVersion.collectAsStateWithLifecycle()
    val isLoadingMoreMods by vm.isLoadingMoreMods.collectAsStateWithLifecycle()
    val isLoadingMorePlugins by vm.isLoadingMorePlugins.collectAsStateWithLifecycle()
    val downloadProgress by vm.pluginDownloadProgress.collectAsStateWithLifecycle()
    val modBatchProgress by vm.modBatchProgress.collectAsStateWithLifecycle()
    val lastModImportReport by vm.lastModImportReport.collectAsStateWithLifecycle()
    val runningFlow = remember(vm) { vm.serverState.map { it.isRunning }.distinctUntilChanged() }
    val isServerRunning by runningFlow.collectAsStateWithLifecycle(initialValue = vm.serverState.value.isRunning)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (modBatchProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("📦 Đang xử lý & giải mã gói Mod...", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Indigo)
                    Text(modBatchProgress ?: "", fontSize = 12.sp, color = Muted)
                }
            },
            confirmButton = {}
        )
    }

    if (lastModImportReport != null) {
        AlertDialog(
            onDismissRequest = { vm.clearModImportReport() },
            title = { Text("🎉 Kết quả Giải mã & Cài đặt Mod", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(lastModImportReport ?: "", fontSize = 12.sp, lineHeight = 18.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearModImportReport() }) {
                    Text("Đã hiểu", color = Indigo, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 全局安装/下载进度对话框（精选插件、Modrinth、自定义 URL 统一反馈）
    val installingEntry = downloadProgress.entries.firstOrNull()
    if (installingEntry != null) {
        val p = installingEntry.value
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.ui_installing), fontSize = 16.sp) },
            text = {
                Column {
                    Text(p.pluginId, fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    if (p.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { (p.downloadedBytes.toFloat() / p.totalBytes).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Indigo
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${formatBytes(p.downloadedBytes)} / ${formatBytes(p.totalBytes)} · ${p.speedBytesPerSec / 1024} KB/s",
                            fontSize = 12.sp, color = Muted
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp), color = Indigo, strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${formatBytes(p.downloadedBytes)} · ${p.speedBytesPerSec / 1024} KB/s",
                            fontSize = 12.sp, color = Muted
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    var activeTab by remember { mutableStateOf(PluginTab.Installed) }
    var pendingDelete by remember { mutableStateOf<PluginManager.InstalledPlugin?>(null) }
    var pendingModDelete by remember { mutableStateOf<PluginManager.ModEntry?>(null) }
    var detailPlugin by remember { mutableStateOf<PluginManager.InstalledPlugin?>(null) }

    // 进入页面或核心切换时自动刷新
    LaunchedEffect(isBootstrapped, config.activeCoreName) {
        if (isBootstrapped && config.activeCoreName != null) {
            vm.refreshInstalledPlugins()
            vm.refreshMods()
        }
    }
    LaunchedEffect(Unit) {
        vm.errorFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.messageFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) }
    }

    val activeCore = config.installedCores.find { it.name == config.activeCoreName }
    val coreType = activeCore?.core ?: config.selectedCore
    val isPowerNukkitX = coreType == com.mineserve.mobile.data.ServerCore.PowerNukkitX
    // Cho phép hiện mục Mod & Gói Mod trên cả Vanilla (tự động nâng cấp tại chỗ lên Fabric giữ nguyên Map khi bật Gói Tối Ưu)
    val availableTypes = buildList {
        if (coreType.supportsPlugins) add(ResourceType.Plugin)
        if (coreType.supportsMods || !coreType.isBedrock) {
            add(ResourceType.Mod)
            add(ResourceType.Modpack)
        }
        add(ResourceType.ResourcePack)
    }
    var resourceType by remember { mutableStateOf(ResourceType.Mod) }
    // 核心切换时校正资源类型（默认取第一个可用类型）
    LaunchedEffect(coreType) {
        if (resourceType !in availableTypes) {
            resourceType = availableTypes.firstOrNull() ?: ResourceType.ResourcePack
        }
    }
    // 进入模组分类时刷新模组列表 + 加载 Modrinth 加载器与游戏版本
    LaunchedEffect(isBootstrapped, config.activeCoreName, resourceType) {
        if (isBootstrapped && config.activeCoreName != null && (resourceType == ResourceType.Mod || resourceType == ResourceType.Modpack)) {
            vm.refreshMods()
            vm.loadModrinthLoaders()
            vm.loadModrinthGameVersions()
        }
        if (isBootstrapped && config.activeCoreName != null && resourceType == ResourceType.ResourcePack) {
            vm.loadServerProperties()
        }
    }
    // 进入插件分类时加载插件 Modrinth 资源
    LaunchedEffect(isBootstrapped, config.activeCoreName, resourceType) {
        if (isBootstrapped && config.activeCoreName != null && resourceType == ResourceType.Plugin) {
            vm.loadPluginModrinthLoaders()
            vm.loadPluginModrinthVersions()
        }
    }
    val pluginsPath = vm.currentPluginsPath()
    val modsPath = remember(pluginsPath) {
        pluginsPath?.replace(Regex("plugins$"), "mods")
    }
    val all56ItemsTop = remember { vm.userScreenshot56ModItems() }
    val matched56Top = remember(mods, all56ItemsTop) {
        all56ItemsTop.count { item ->
            val slugLower = item.slug.lowercase(java.util.Locale.ROOT)
            val slugNorm = slugLower.replace("-", "").replace("_", "")
            val jarPrefix = item.originalJarName.substringBefore('-').substringBefore('_').lowercase(java.util.Locale.ROOT)
            val jarNorm = jarPrefix.replace("-", "").replace("_", "")
            mods.any { mod ->
                val baseLower = mod.baseName.lowercase(java.util.Locale.ROOT)
                val baseNorm = baseLower.replace("-", "").replace("_", "")
                baseLower.startsWith(slugLower) ||
                    (jarPrefix.length >= 3 && baseLower.startsWith(jarPrefix)) ||
                    baseLower.contains(slugLower) ||
                    (slugNorm.length >= 4 && baseNorm.startsWith(slugNorm)) ||
                    (jarNorm.length >= 4 && baseNorm.startsWith(jarNorm))
            }
        }
    }
    // 插件冲突检测接近 O(n²)，用 remember 缓存，避免每次重组全量重算
    val pluginConflicts = remember(installedPlugins) { vm.detectPluginConflicts(installedPlugins) }

    Scaffold(
        snackbarHost = { SwipeableInstantSnackbarHost(snackbarHostState) },
        // 嵌套在外层 McApp Scaffold 内：insets 已由外层消费，这里不再重复应用，避免顶部空白/白色遮挡
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (isPowerNukkitX) {
                Text(
                    stringResource(R.string.pl_pnx_mods_hint),
                    color = Coral,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            HeaderBlock(eyebrow = stringResource(R.string.eyebrow_plugins), title = stringResource(R.string.s750))

            TextButton(onClick = onAddonTools, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Addon PE, Voice Chat & Datapack →")
            }

            // ── 当前核心状态卡片 ──
            McCard(
                title = stringResource(R.string.s751),
                trailing = {
                    Text(
                        stringResource(R.string.s333),
                        color = Indigo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            if (activeCore != null) {
                                vm.refreshInstalledPlugins()
                                vm.refreshMods()
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.s752)) }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.s753)) }
                            }
                        }
                    )
                }
            ) {
                if (activeCore == null) {
                    Text(
                        stringResource(R.string.s754),
                        color = Coral,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    Text(
                        "${activeCore.name}  ·  ${activeCore.core.displayName} ${activeCore.version}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    if (coreType.supportsMods && !coreType.supportsPlugins) {
                        val enabledCount = mods.count { it.isEnabled }
                        val disabledCount = mods.count { !it.isEnabled }
                        Text(
                            "Thư mục mod: ${modsPath ?: "--"}",
                            color = Muted,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (mods.isEmpty()) Muted else Mint)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                if (mods.isEmpty()) {
                                    "Chưa tải mod nào trong thư mục mods/"
                                } else {
                                    "✅ Đã tải & cài đặt: ${mods.size} mod (Đang bật: $enabledCount" +
                                        (if (disabledCount > 0) " · Đang tắt: $disabledCount" else "") + ")"
                                },
                                color = if (mods.isEmpty()) Muted else Mint,
                                fontSize = 11.sp,
                                fontWeight = if (mods.isEmpty()) FontWeight.Normal else FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.s755, pluginsPath ?: "--"),
                            color = Muted,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (installedPlugins.isEmpty() && mods.isEmpty()) Muted else Mint)
                            )
                            Spacer(Modifier.size(6.dp))
                            val pluginSummary = if (installedPlugins.isEmpty()) {
                                stringResource(R.string.s756, 0)
                            } else {
                                "✅ Đã tải & cài đặt ${installedPlugins.size} plugin" +
                                    if (installedPlugins.count { !it.isEnabled } > 0)
                                        stringResource(R.string.s757, installedPlugins.count { !it.isEnabled })
                                    else ""
                            }
                            val modHybridSummary = if (coreType.supportsMods && mods.isNotEmpty()) {
                                " · ✅ Đã cài ${mods.size} mod"
                            } else ""
                            Text(
                                pluginSummary + modHybridSummary,
                                color = if (installedPlugins.isEmpty() && mods.isEmpty()) Muted else Mint,
                                fontSize = 11.sp,
                                fontWeight = if (installedPlugins.isEmpty() && mods.isEmpty()) FontWeight.Normal else FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (coreType.supportsPlugins) stringResource(R.string.s467) else stringResource(R.string.s468),
                            color = if (coreType.supportsPlugins) Mint else Coral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (coreType.supportsMods) stringResource(R.string.s469) else stringResource(R.string.s470),
                            color = if (coreType.supportsMods) Mint else Coral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── GÓI MOD TỐI ƯU SERVER CỰC MẠNH LƯU SẴN (BẬT 1 CHẠM — TỰ ĐỘNG KHỚP PHIÊN BẢN) ──
            if (activeCore != null && !coreType.isBedrock) {
                val optPackStatus by vm.serverOptPackStatus.collectAsStateWithLifecycle()
                val isTogglingOptPack by vm.isTogglingServerOptPack.collectAsStateWithLifecycle()
                val optPackProgress by vm.serverOptPackProgress.collectAsStateWithLifecycle()
                val optBundle = remember { vm.serverOptimizationModBundle() }
                val detectedMcVer = (activeCore.version.ifBlank { config.mcVersion }).ifBlank { "1.21.11" }
                var showAllOptMods by remember { mutableStateOf(false) }

                McCard(
                    title = "🚀 Gói Mod Tối Ưu Server Lưu Sẵn (Bật 1 Chạm)",
                    trailing = {
                        Switch(
                            checked = optPackStatus.isPackActive,
                            onCheckedChange = { wantEnable ->
                                vm.toggleServerOptimizationPack(enable = wantEnable, customMcVersion = detectedMcVer)
                            },
                            enabled = !isTogglingOptPack
                        )
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (optPackStatus.isPackActive) MintSoft else IndigoSoft)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (optPackStatus.isPackActive) {
                                    "🟢 ĐANG BẬT: ${optPackStatus.enabledCount}/${optPackStatus.totalModsInPack} Mod Tối Ưu Cực Mạnh (MC $detectedMcVer)"
                                } else {
                                    "⚡ Chưa bật Gói Tối Ưu · Tự động khớp phiên bản: MC $detectedMcVer"
                                },
                                color = if (optPackStatus.isPackActive) Mint else Indigo,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "✅ Chỉ cài cho Server (100% Server-Side) — Người chơi PC / Pojav / FCL vào bình thường KHÔNG CẦN cài mod!" +
                                    if (optPackStatus.cachedCount > 0) " · Đã lưu sẵn trong máy: ${optPackStatus.cachedCount} mod" else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    if (!coreType.supportsMods) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "💡 Máy chủ hiện tại đang là ${coreType.displayName} $detectedMcVer: Khi bạn gạt bật công tắc hoặc bấm nút bên dưới, ứng dụng sẽ TỰ ĐỘNG nâng cấp lõi lên Fabric $detectedMcVer (giữ nguyên 100% Map, người chơi & cài đặt) và nạp trọn bộ 14 Mod tối ưu!",
                            color = Indigo,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (optPackProgress != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(FieldGray)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Indigo
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                optPackProgress ?: "",
                                color = Indigo,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    // Hiển thị danh sách các Mod tối ưu kết hợp trong gói
                    val displayedItems = if (showAllOptMods) optBundle else optBundle.take(6)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        displayedItems.forEach { item ->
                            val isEnabledNow = item.id in optPackStatus.enabledModIds
                            val isInstalledNow = item.id in optPackStatus.installedModIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isEnabledNow) MintSoft.copy(alpha = 0.65f) else FieldGray)
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AddonJarIcon(
                                    baseName = item.displayName,
                                    slugHint = item.slugs.firstOrNull() ?: item.id,
                                    isEnabled = isEnabledNow,
                                    size = 34.dp
                                )
                                Spacer(Modifier.size(9.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            item.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(IndigoSoft)
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                item.impactBadge,
                                                color = Indigo,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(MintSoft)
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                item.categoryBadge,
                                                color = Mint,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    AddonSideBadge(if (item.id == "fabric-api") AddonSide.LIBRARY else AddonSide.SERVER_ONLY)
                                    Text(
                                        item.descriptionVi,
                                        color = Muted,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    when {
                                        isEnabledNow -> "🟢 Đang bật"
                                        isInstalledNow -> "⏸ Lưu sẵn"
                                        else -> "⚡ Sẵn sàng"
                                    },
                                    color = if (isEnabledNow) Mint else Muted,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = { showAllOptMods = !showAllOptMods },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            if (showAllOptMods) "Thu gọn danh sách ▲" else "Xem đủ ${optBundle.size} Mod tối ưu cực mạnh ▼",
                            color = Indigo,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                vm.toggleServerOptimizationPack(
                                    enable = !optPackStatus.isPackActive,
                                    customMcVersion = detectedMcVer
                                )
                            },
                            enabled = !isTogglingOptPack,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (optPackStatus.isPackActive) Coral else Mint
                            ),
                            shape = McShape.ControlCompact,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (optPackStatus.isPackActive) {
                                    "⏸ Tạm tắt Gói Mod Tối Ưu Server"
                                } else {
                                    "🚀 BẬT NGAY 14 MOD TỐI ƯU SERVER (MC $detectedMcVer)"
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    if (isServerRunning) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "💡 Server đang chạy: Khi bật/tắt Gói Mod Tối Ưu, App sẽ tự động lưu thế giới, nạp Mod và khởi động lại Server.",
                            color = Indigo,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── NÂNG CẤP PRO MAX: SIMPLE VOICE CHAT 1 CHẠM & XƯỞNG GẮN RESOURCE PACK / VIỆT HÓA (TỰ BĂM SHA-1) ──
            val voiceChatStatus by vm.voiceChatStatus.collectAsStateWithLifecycle()
            val resourcePackInfo by vm.serverResourcePackInfo.collectAsStateWithLifecycle()
            val liveTask by vm.liveTaskProgress.collectAsStateWithLifecycle()
            var customRpUrl by remember { mutableStateOf("") }
            var requireRpToggle by remember(resourcePackInfo.requirePack) { mutableStateOf(resourcePackInfo.requirePack) }
            LaunchedEffect(config.activeCoreName) {
                vm.refreshVoiceChatAndResourcePackStatus()
            }

            McCard(
                title = "🎙️ Simple Voice Chat 1 Chạm & 🎨 Xưởng Resource Pack / Việt Hóa"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (liveTask.active) {
                        val pct = (liveTask.progress.coerceIn(0f, 1f) * 100).toInt()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(IndigoSoft)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(liveTask.title, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Indigo)
                                Text("${pct}%", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Mint)
                            }
                            LinearProgressIndicator(
                                progress = { liveTask.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                                color = Mint,
                                trackColor = Indigo.copy(alpha = 0.2f)
                            )
                            if (liveTask.detail.isNotBlank()) {
                                Text(liveTask.detail, fontSize = 10.sp, color = Muted)
                            }
                        }
                    }

                    // 1. Simple Voice Chat 1-Tap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🎙️ Đàm Thoại Trong Game 1 Chạm (Simple Voice Chat Pro)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Indigo
                            )
                            Text(
                                if (voiceChatStatus.isInstalled) {
                                    "✅ Đã cài (${voiceChatStatus.fileName}) • Cổng UDP: ${voiceChatStatus.udpPort}"
                                } else {
                                    "Tự tải đúng bản Fabric/Forge/NeoForge/Paper & cấu hình sẵn cổng UDP 24454"
                                },
                                fontSize = 11.sp,
                                color = if (voiceChatStatus.isInstalled) Mint else Muted,
                                fontWeight = if (voiceChatStatus.isInstalled) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Button(
                            onClick = { vm.installSimpleVoiceChatOneTap() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (voiceChatStatus.isInstalled) Mint else Indigo
                            ),
                            shape = McShape.ControlCompact
                        ) {
                            Text(
                                if (voiceChatStatus.isInstalled) "🔄 Cập nhật Voice" else "🎙️ Cài Ngay 1 Chạm",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // 2. Xưởng Gắn Resource Pack & Việt Hóa Server (Tự tính SHA-1)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(FieldGray)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "🎨 Xưởng Gắn Resource Pack & Việt Hóa Server (Tự Băm SHA-1 Chuẩn):",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = Indigo
                        )
                        if (resourcePackInfo.url.isNotBlank()) {
                            Text(
                                "✅ Đang gắn: ${resourcePackInfo.url.take(55)}${if (resourcePackInfo.url.length > 55) "..." else ""}\n🔐 SHA-1: ${resourcePackInfo.sha1.ifBlank { "Tự động" }}",
                                fontSize = 10.5.sp,
                                color = Mint,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                "Chọn nhanh gói có sẵn hoặc dán link .zip để mọi người vào Server tự động được tải về:",
                                fontSize = 10.5.sp,
                                color = Muted
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            com.mineserve.mobile.server.ProHostFeatures.RESOURCE_PACK_PRESETS.forEach { preset ->
                                OutlinedButton(
                                    onClick = {
                                        vm.applyServerResourcePackOneTap(
                                            packUrl = preset.url,
                                            promptVi = preset.promptVi,
                                            requirePack = requireRpToggle
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(preset.titleVi, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customRpUrl,
                                onValueChange = { customRpUrl = it },
                                label = { Text("Hoặc dán link trực tiếp .zip Resource Pack", fontSize = 10.5.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    vm.applyServerResourcePackOneTap(
                                        packUrl = customRpUrl,
                                        promptVi = "Tải gói tài nguyên của Server để có trải nghiệm tốt nhất!",
                                        requirePack = requireRpToggle
                                    )
                                },
                                enabled = customRpUrl.isNotBlank(),
                                shape = McShape.ControlCompact
                            ) {
                                Text("Gắn & Băm SHA-1", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Bắt buộc người chơi phải tải gói tài nguyên (require-resource-pack)",
                                fontSize = 10.5.sp,
                                color = Muted,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = requireRpToggle,
                                onCheckedChange = { requireRpToggle = it }
                            )
                        }
                    }
                }
            }

            // ── 4 Phân mục Tài nguyên: Plugin / Mod / Gói Mod & ZIP / Gói Tài Nguyên ──
            if (availableTypes.isEmpty()) {
                Text(
                    stringResource(R.string.s758, coreType.displayName),
                    color = Coral,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = McSpacing.Page, vertical = 4.dp)
                )
            } else {
                val types = availableTypes
                SegmentedTabs(
                    options = types.map { type ->
                        when (type) {
                            ResourceType.Plugin -> if (installedPlugins.isNotEmpty()) "${type.titleVi} (${installedPlugins.size})" else type.titleVi
                            ResourceType.Mod -> if (mods.isNotEmpty()) "${type.titleVi} (${mods.size})" else type.titleVi
                            ResourceType.Modpack -> type.titleVi
                            ResourceType.ResourcePack -> type.titleVi
                        }
                    },
                    selectedIndex = types.indexOf(resourceType).coerceAtLeast(0),
                    modifier = Modifier.padding(horizontal = McSpacing.Page, vertical = 4.dp),
                    onSelect = { index -> types.getOrNull(index)?.let { resourceType = it } }
                )
            }

            // ── 标签切换栏（仅插件分类显示） ──
            if (resourceType == ResourceType.Plugin) {
                val tabs = PluginTab.values()
                SegmentedTabs(
                    options = tabs.map { tab ->
                        val count = if (tab == PluginTab.Installed) installedPlugins.size else 0
                        if (count > 0) "${stringResource(tab.labelRes)} $count" else stringResource(tab.labelRes)
                    },
                    selectedIndex = tabs.indexOf(activeTab),
                    modifier = Modifier.padding(horizontal = McSpacing.Page, vertical = 4.dp),
                    onSelect = { index -> tabs.getOrNull(index)?.let { activeTab = it } }
                )
            }

            // ── 内容区（Plugin / Mod / Gói Mod & ZIP / Gói Tài Nguyên） ──
            when (resourceType) {
                ResourceType.Plugin -> {
                    when (activeTab) {
                        PluginTab.Installed -> InstalledTab(
                            installed = installedPlugins,
                            conflicts = pluginConflicts,
                            activeCoreExists = activeCore != null,
                            serverRunning = isServerRunning,
                            onToggle = { vm.togglePluginEnabled(it.fileName) },
                            onDelete = { pendingDelete = it },
                            onShowDetail = { detailPlugin = it }
                        )

                        PluginTab.Upload -> UploadTab(
                            activeCoreExists = activeCore != null,
                            customUrlProgress = vm.customUrlDownloadProgress(),
                            onPickFile = { uri -> vm.installPluginFromUri(uri) },
                            onInstallFromUrl = { url, name -> vm.installPluginFromUrl(url, name) },
                            onGotoInstalled = { activeTab = PluginTab.Installed }
                        )
                    }
                }
                ResourceType.Mod -> {
                    ModsTab(
                        vm = vm,
                        mods = mods,
                        curatedMods = vm.curatedMods,
                        activeCoreExists = activeCore != null,
                        coreType = coreType,
                        currentServerVersion = activeCore?.version ?: config.mcVersion,
                        serverRunning = isServerRunning,
                        modrinthResults = modrinthResults,
                        modrinthLoaders = modrinthLoaders,
                        modrinthGameVersions = modrinthGameVersions,
                        selectedModVersion = selectedModVersion,
                        onSetModVersion = { vm.setSelectedModVersion(it) },
                        onToggle = { vm.toggleModEnabled(it) },
                        onDelete = { pendingModDelete = it },
                        onUpload = { uri -> vm.installModFromUri(uri) },
                        onInstallCurated = { vm.installCuratedMod(it) },
                        onSearchModrinth = { query, loaders, sort, version, category ->
                            vm.searchModrinthMods(query, loaders, sort, version, category)
                        },
                        onInstallModrinth = { hit, version -> vm.installModrinthMod(hit, version) },
                        onLoadMoreModrinth = { vm.loadMoreModrinthMods() },
                        isLoadingMore = isLoadingMoreMods
                    )
                }
                ResourceType.Modpack -> {
                    ModPackDecoderAndTransferCard(
                        vm = vm,
                        activeCoreExists = activeCore != null,
                        serverRunning = isServerRunning,
                        hasInstalledMods = mods.isNotEmpty(),
                        showFull56ListByDefault = false
                    )
                }
                ResourceType.ResourcePack -> {
                    ResourcePackConfigCard(vm = vm, activeCoreExists = activeCore != null)
                }
            }

            // ── 插件页 Modrinth 资源检索（与模组页布局统一） ──
            if (resourceType == ResourceType.Plugin) {
                PluginModrinthCard(
                    results = pluginModrinthResults,
                    installedPlugins = installedPlugins,
                    loaders = pluginModrinthLoaders,
                    gameVersions = modrinthGameVersions,
                    selectedVersion = selectedPluginVersion,
                    currentServerVersion = activeCore?.version ?: config.mcVersion,
                    onSetVersion = { vm.setSelectedPluginVersion(it) },
                    onSearch = { query, loaders, sort, version, category ->
                        vm.searchModrinthPlugin(query, loaders, sort, version, category)
                    },
                    onInstall = { hit, version -> vm.installModrinthPlugin(hit, version) },
                    onLoadMore = { vm.loadMoreModrinthPlugin() },
                    isLoadingMore = isLoadingMorePlugins
                )
            }

            // ── 底部热重载 ──
            McCard(title = stringResource(R.string.s759)) {
                Text(
                    if (isServerRunning)
                        stringResource(R.string.s760)
                    else
                        stringResource(R.string.s761),
                    color = if (isServerRunning) Muted else Coral,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        vm.sendCommand("reload")
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.s762)) }
                    },
                    enabled = isServerRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Indigo,
                        disabledContainerColor = IndigoSoft
                    ),
                    shape = McShape.Control,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (isServerRunning) stringResource(R.string.s763) else stringResource(R.string.s280),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 底部配置提示
            Text(
                stringResource(R.string.s764),
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // ── 插件资源站点 ──
            Spacer(Modifier.height(10.dp))
            McCard(title = stringResource(R.string.s765)) {
                Text(
                    stringResource(R.string.s766),
                    color = Muted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(8.dp))
                val context = LocalContext.current
                pluginSites.forEach { site ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site.url)))
                                } catch (e: Exception) { /* 无浏览器时忽略 */ }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(site.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(site.descRes),
                                color = Muted,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = stringResource(R.string.s767),
                            tint = Indigo,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // 删除确认对话框（含数据目录清理选项）
    pendingDelete?.let { plugin ->
        DeletePluginDialog(
            plugin = plugin,
            onConfirm = { alsoRemoveData ->
                vm.deletePlugin(plugin.fileName, alsoRemoveData)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    // 模组删除确认对话框
    pendingModDelete?.let { mod ->
        AlertDialog(
            onDismissRequest = { pendingModDelete = null },
            title = { Text(stringResource(R.string.s768), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.s769, mod.baseName),
                    color = Muted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMod(mod.fileName)
                    pendingModDelete = null
                }) {
                    Text(stringResource(R.string.s339), color = Coral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingModDelete = null }) {
                    Text(stringResource(R.string.s402), color = Muted)
                }
            }
        )
    }

    // 插件详情对话框
    detailPlugin?.let { plugin ->
        PluginDetailDialog(
            plugin = plugin,
            onDismiss = { detailPlugin = null }
        )
    }
}

// ── 已安装标签 ──────────────────────────────────────────────────────

@Composable
private fun InstalledTab(
    installed: List<PluginManager.InstalledPlugin>,
    conflicts: List<String>,
    activeCoreExists: Boolean,
    serverRunning: Boolean,
    onToggle: (PluginManager.InstalledPlugin) -> Unit,
    onDelete: (PluginManager.InstalledPlugin) -> Unit,
    onShowDetail: (PluginManager.InstalledPlugin) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(InstalledFilter.All) }

    McCard(title = stringResource(R.string.s390)) {
        if (!activeCoreExists) {
            EmptyHint(icon = Icons.Outlined.Extension, text = stringResource(R.string.s770))
            return@McCard
        }
        if (installed.isEmpty()) {
            EmptyHint(icon = Icons.Outlined.Extension, text = stringResource(R.string.s771))
            return@McCard
        }

        // 服务端运行中禁止启用/禁用或删除插件（文件正被 JVM 占用，改动不会生效且易损坏）
        if (serverRunning) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.pl_running_block), color = Coral, fontSize = 11.sp, lineHeight = 15.sp)
        }

        // 插件冲突警告
        if (conflicts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Coral.copy(alpha = 0.12f))
                    .border(1.dp, Coral, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(stringResource(R.string.pl_conflict_title), color = Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    conflicts.forEach { c ->
                        Text("• $c", color = Coral, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }

        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.s772), fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = McShape.ControlCompact
        )

        Spacer(Modifier.height(8.dp))

        // 筛选标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InstalledFilter.values().forEach { filter ->
                val count = when (filter) {
                    InstalledFilter.All -> installed.size
                    InstalledFilter.Enabled -> installed.count { it.isEnabled }
                    InstalledFilter.Disabled -> installed.count { !it.isEnabled }
                    InstalledFilter.Local -> installed.count { it.sourceTag == "本地" || it.sourceTag == "Cục bộ" || it.sourceTag == "Local" }
                }
                val label = "${stringResource(filter.labelRes)} $count"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeFilter == filter) Indigo else IndigoSoft)
                        .clickable { activeFilter = filter }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        label,
                        color = if (activeFilter == filter) Color.White else Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 过滤 + 搜索
        val filtered = remember(installed, activeFilter, searchQuery) { installed.filter { p ->
            (activeFilter == InstalledFilter.All ||
                (activeFilter == InstalledFilter.Enabled && p.isEnabled) ||
                (activeFilter == InstalledFilter.Disabled && !p.isEnabled) ||
                (activeFilter == InstalledFilter.Local && (p.sourceTag == "本地" || p.sourceTag == "Cục bộ" || p.sourceTag == "Local"))) &&
                (searchQuery.isBlank() || p.baseName.contains(searchQuery, ignoreCase = true) ||
                    (p.meta?.name?.contains(searchQuery, ignoreCase = true) == true))
        }
        }

        if (filtered.isEmpty()) {
            EmptyHint(icon = Icons.Outlined.Extension, text = stringResource(R.string.s773))
            return@McCard
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.fileName }) { p ->
                InstalledPluginRow(
                    plugin = p,
                    enabled = !serverRunning,
                    onToggle = { onToggle(p) },
                    onDelete = { onDelete(p) },
                    onShowDetail = { onShowDetail(p) }
                )
            }
        }
    }
}

@Composable
private fun InstalledPluginRow(
    plugin: PluginManager.InstalledPlugin,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onShowDetail: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (plugin.isEnabled) IndigoSoft else CoralSoft.copy(alpha = 0.4f))
            .clickable { onShowDetail() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AddonJarIcon(
            jarPath = plugin.filePath,
            baseName = plugin.meta?.name ?: plugin.baseName,
            slugHint = plugin.meta?.name ?: plugin.baseName,
            isEnabled = plugin.isEnabled,
            size = 36.dp
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plugin.meta?.name ?: plugin.baseName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (plugin.isEnabled) MaterialTheme.colorScheme.onSurface else Muted
                )
                if (plugin.meta?.version != null) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "v${plugin.meta.version}",
                        color = Muted,
                        fontSize = 9.sp
                    )
                }
            }
            val metaLine = buildString {
                append(plugin.sizeText)
                append("  ·  ")
                append(plugin.lastModifiedText)
                append("  ·  ")
                append(plugin.sourceTag)
                if (!plugin.isEnabled) append(stringResource(R.string.pl_disabled_suffix))
            }
            Text(
                metaLine,
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AddonInfoLines(rememberPluginInfo(plugin), modifier = Modifier.padding(top = 3.dp), descMaxLines = 2)
        }
        // 详情按钮
        Box(
            modifier = Modifier
                .size(McIcon.TouchCompact)
                .clip(McShape.ControlCompact)
                .background(Color.Transparent)
                .clickable { onShowDetail() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.s734), tint = Indigo, modifier = Modifier.size(McIcon.Inline))
        }
        // 启用/禁用开关
        Switch(
            checked = plugin.isEnabled,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            modifier = Modifier.size(width = 36.dp, height = 20.dp)
        )
        Spacer(Modifier.size(6.dp))
        // 删除按钮
        Box(
            modifier = Modifier
                .size(McIcon.TouchCompact)
                .clip(McShape.ControlCompact)
                .background(CoralSoft)
                .clickable(enabled = enabled) { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.s339), tint = Coral, modifier = Modifier.size(McIcon.Inline))
        }
    }
}

// ── 本地上传标签 ────────────────────────────────────────────────────

@Composable
private fun UploadTab(
    activeCoreExists: Boolean,
    customUrlProgress: McViewModel.PluginDownloadProgress?,
    onPickFile: (Uri) -> Unit,
    onInstallFromUrl: (String, String) -> Unit,
    onGotoInstalled: () -> Unit
) {
    // SAF 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) onPickFile(uri)
    }

    var customUrl by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    McCard(title = stringResource(R.string.s792)) {
        if (!activeCoreExists) {
            EmptyHint(icon = Icons.Outlined.Extension, text = stringResource(R.string.s770))
            return@McCard
        }
        Text(
            stringResource(R.string.s793),
            color = Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.s794),
            color = Muted,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(16.dp))

        // 大按钮区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(IndigoSoft)
                .border(width = 1.dp, color = Indigo, shape = McShape.Control)
                .clickable {
                    filePickerLauncher.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*"))
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = stringResource(R.string.s497), tint = Indigo, modifier = Modifier.size(36.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.s795), color = Indigo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(2.dp))
                Text(stringResource(R.string.s796), color = Muted, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        // 分隔线
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(IndigoSoft))
            Text(stringResource(R.string.s797), color = Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(IndigoSoft))
        }

        Spacer(Modifier.height(16.dp))

        // 自定义 URL 下载
        Text(stringResource(R.string.s798), color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.s799),
            color = Muted,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = customUrl,
            onValueChange = { customUrl = it },
            label = { Text(stringResource(R.string.s800), fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(14.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = McShape.ControlCompact,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = customName,
            onValueChange = { customName = it },
            label = { Text(stringResource(R.string.s801), fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = McShape.ControlCompact
        )

        // 自定义 URL 下载进度
        if (customUrlProgress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { customUrlProgress.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Mint,
                trackColor = IndigoSoft
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${customUrlProgress.percent}%", color = Indigo, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text(customUrlProgress.speedText, color = Muted, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                onInstallFromUrl(customUrl.trim(), customName.trim())
            },
            enabled = customUrl.isNotBlank() && customName.isNotBlank() && customUrlProgress == null,
            colors = ButtonDefaults.buttonColors(containerColor = Indigo),
            shape = McShape.ControlCompact,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.s486), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onGotoInstalled,
            shape = McShape.ControlCompact,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.s802), color = Indigo, fontSize = 11.sp)
        }
    }
}

// ── 对话框组件 ──────────────────────────────────────────────────────

@Composable
private fun DeletePluginDialog(
    plugin: PluginManager.InstalledPlugin,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var alsoRemoveData by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s803), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(stringResource(R.string.s804), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    plugin.fileName,
                    color = Indigo,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.s805, plugin.sizeText, plugin.lastModifiedText),
                    color = Muted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.s806),
                    color = Coral,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { alsoRemoveData = !alsoRemoveData }
                ) {
                    Checkbox(
                        checked = alsoRemoveData,
                        onCheckedChange = { alsoRemoveData = it }
                    )
                    Spacer(Modifier.size(4.dp))
                    Column {
                        Text(stringResource(R.string.s807), fontSize = 12.sp)
                        Text(
                            if (plugin.meta?.name != null)
                                stringResource(R.string.s808, plugin.meta.name)
                            else
                                stringResource(R.string.s809),
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(alsoRemoveData) },
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                shape = McShape.Control
            ) { Text(stringResource(R.string.s339), color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.s402)) }
        }
    )
}

@Composable
private fun PluginDetailDialog(
    plugin: PluginManager.InstalledPlugin,
    onDismiss: () -> Unit
) {
    val meta = plugin.meta
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Extension, contentDescription = null, tint = Indigo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.s810), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DetailRow(stringResource(R.string.s811), plugin.fileName)
                DetailRow(stringResource(R.string.s812), plugin.baseName)
                if (meta != null) {
                    DetailRow(stringResource(R.string.s813), meta.name)
                    DetailRow(stringResource(R.string.s814), meta.version)
                    DetailRow(stringResource(R.string.s815), meta.mainClass.ifEmpty { "—" })
                    DetailRow(stringResource(R.string.s816), meta.author)
                    DetailRow(stringResource(R.string.s817), meta.apiVersion.ifEmpty { "—" })
                    if (meta.description.isNotBlank()) {
                        DetailRow(stringResource(R.string.s818), meta.description)
                    }
                    if (meta.depends.isNotEmpty()) {
                        DetailRow(stringResource(R.string.s819), meta.depends.joinToString(", "))
                    }
                    if (meta.softDepends.isNotEmpty()) {
                        DetailRow(stringResource(R.string.s820), meta.softDepends.joinToString(", "))
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.s821),
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
                DetailRow(stringResource(R.string.s822), plugin.sizeText)
                DetailRow(stringResource(R.string.s823), plugin.lastModifiedText)
                DetailRow(stringResource(R.string.s725), plugin.sourceTag)
                DetailRow(stringResource(R.string.s824), if (plugin.isEnabled) stringResource(R.string.s748) else stringResource(R.string.s237))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.s620)) }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

// ── 通用组件 ──

@Composable
private fun BadgeChip(text: String, color: Color, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 模组分类 ──────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ModsTab(
    vm: McViewModel,
    mods: List<PluginManager.ModEntry>,
    curatedMods: List<PluginManager.CuratedMod>,
    activeCoreExists: Boolean,
    coreType: ServerCore,
    currentServerVersion: String,
    modrinthResults: List<PluginManager.ModrinthHit>,
    modrinthLoaders: List<String>,
    modrinthGameVersions: List<String>,
    selectedModVersion: String,
    onSetModVersion: (String) -> Unit,
    serverRunning: Boolean,
    onToggle: (String) -> Unit,
    onDelete: (PluginManager.ModEntry) -> Unit,
    onUpload: (Uri) -> Unit,
    onInstallCurated: (PluginManager.CuratedMod) -> Unit,
    onSearchModrinth: (String, List<String>, String, String, String) -> Unit,
    onInstallModrinth: (PluginManager.ModrinthHit, String) -> Unit,
    onLoadMoreModrinth: () -> Unit,
    isLoadingMore: Boolean
) {
    var detailHit by remember { mutableStateOf<PluginManager.ModrinthHit?>(null) }
    var modrinthQuery by remember { mutableStateOf("") }
    var selectedModCategory by remember { mutableStateOf("") }
    var installedModSearch by remember { mutableStateOf("") }
    var installedModFilter by remember { mutableStateOf(InstalledFilter.All) }
    var pendingModToggle by remember { mutableStateOf<PluginManager.ModEntry?>(null) }
    var pendingBulkModAction by remember { mutableStateOf<String?>(null) }
    var selectedLoaders by remember { mutableStateOf(setOf<String>()) }
    var sortIndex by remember { mutableStateOf(0) }
    val sortOptions = listOf("downloads" to stringResource(R.string.s825), "relevance" to stringResource(R.string.s826), "newest" to stringResource(R.string.s827))
    LaunchedEffect(selectedModVersion, coreType) {
        if (modrinthResults.isEmpty()) {
            onSearchModrinth("", selectedLoaders.toList(), "downloads", selectedModVersion.ifBlank { currentServerVersion }, selectedModCategory)
        }
    }
    // 本地上传模组选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onUpload) }

    // 已安装模组 (Quản lý Mod trực quan + Lọc Bật/Tắt + Công tắc có cảnh báo)
    val enabledModsCount = mods.count { it.isEnabled }
    val disabledModsCount = mods.count { !it.isEnabled }
    val totalModsBytes = remember(mods) { mods.sumOf { it.sizeBytes } }
    val filteredInstalledMods = remember(mods, installedModFilter, installedModSearch) {
        mods.filter { mod ->
            val matchFilter = when (installedModFilter) {
                InstalledFilter.All -> true
                InstalledFilter.Enabled -> mod.isEnabled
                InstalledFilter.Disabled -> !mod.isEnabled
                InstalledFilter.Local -> true
            }
            val matchQuery = installedModSearch.isBlank() ||
                mod.baseName.contains(installedModSearch, ignoreCase = true) ||
                mod.fileName.contains(installedModSearch, ignoreCase = true) ||
                (mod.displayName?.contains(installedModSearch, ignoreCase = true) == true)
            matchFilter && matchQuery
        }
    }

    McCard(
        title = if (mods.isEmpty()) {
            stringResource(R.string.s828)
        } else {
            "🧩 Quản lý Mod trực quan (${mods.size} Mod • 💾 ${vm.formatStorageBytes(totalModsBytes)})"
        }
    ) {
        if (serverRunning) {
            // 服务端运行中禁止启用/禁用或删除模组（文件正被 JVM 占用，改动不会生效且易损坏）
            Text(stringResource(R.string.pl_running_block), color = Coral, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(6.dp))
        }
        if (mods.isEmpty()) {
            Text(
                stringResource(R.string.s829),
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            val modDoctorBusy by vm.modDoctorBusy.collectAsStateWithLifecycle()
            val modDoctorProgress by vm.modDoctorProgress.collectAsStateWithLifecycle()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF064E3B).copy(alpha = 0.25f))
                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "🛠️ Bác Sĩ Tự Động Sửa Lỗi Mod (Chống Văng Server FormattedException)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = Color(0xFF34D399)
                )
                Text(
                    "Tự động mổ xẻ toàn bộ ${mods.size} tệp .jar: tải bù thư viện thiếu (Fabric API...), thay thế hoặc tạm tắt các Mod bị lệch phiên bản Minecraft ($currentServerVersion) chỉ với 1 chạm:",
                    fontSize = 11.sp,
                    lineHeight = 14.5.sp
                )
                if (modDoctorBusy && !modDoctorProgress.isNullOrBlank()) {
                    Text(
                        modDoctorProgress.orEmpty(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.autoFixModCrashAndRestart(autoRestart = true) },
                        enabled = !serverRunning && !modDoctorBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669), contentColor = Color.White),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text(
                            if (modDoctorBusy) "⏳ Đang sửa..." else "🚀 Tự Sửa Mod & Bật Server",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { vm.autoFixModCrashAndRestart(autoRestart = false) },
                        enabled = !serverRunning && !modDoctorBusy,
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔧 Chỉ Quét & Sửa", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // ── Thanh thao tác hàng loạt thông minh: Tắt toàn bộ Mod / Bật toàn bộ Mod / Chỉ bật Mod Tối ưu & Lõi ──
            val pureOptPrefixes = remember {
                listOf(
                    "fabric-api", "lithium", "ferritecore", "krypton", "c2me", "noisium",
                    "modernfix", "immediatelyfast", "entityculling", "alternate-current",
                    "alternate_current", "chunky", "spark", "ksyxis", "servercore", "clumps",
                    "very-many-players", "vmp", "starlight", "lazydfu", "memoryleakfix"
                )
            }
            val serilumDependents = remember {
                listOf(
                    "treeharvester", "starterkit", "healingcampfire", "villagernames",
                    "doubledoors", "inventorytotem", "areas", "edibles", "naturalslabs"
                )
            }
            val disabledCoreLibs = remember(mods) {
                val enabledNorms = mods.filter { it.isEnabled }.map {
                    it.baseName.lowercase(java.util.Locale.ROOT).replace("_", "-")
                }
                val hasAnyEnabled = enabledNorms.isNotEmpty()
                val hasNonOptGameplayMod = enabledNorms.any { norm ->
                    pureOptPrefixes.none { opt -> norm.contains(opt) }
                }
                val hasSerilumMod = enabledNorms.any { norm ->
                    serilumDependents.any { dep -> norm.contains(dep) }
                }
                mods.filter { mod ->
                    if (mod.isEnabled) return@filter false
                    val norm = mod.baseName.lowercase(java.util.Locale.ROOT).replace("_", "-")
                    when {
                        norm.contains("fabric-api") -> hasAnyEnabled
                        norm.contains("collective") -> hasSerilumMod
                        norm.contains("fabric-language-kotlin") ||
                            norm.contains("architectury") ||
                            norm.contains("cloth-config") ||
                            norm.contains("balm") ||
                            norm.contains("puzzleslib") ||
                            norm.contains("forgeconfigapiport") ||
                            norm.contains("craterlib") -> hasNonOptGameplayMod
                        else -> false
                    }
                }
            }
            if (enabledModsCount > 0 && disabledCoreLibs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CoralSoft)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "🤖 Trợ lý Thông Minh phát hiện ${disabledCoreLibs.size} Thư viện Lõi đang bị tắt (${disabledCoreLibs.joinToString { it.baseName }})",
                        color = Coral,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Khi đang bật các Mod khác mà tắt Thư viện Lõi (như Fabric API, Architectury, Balm...), Server sẽ báo lỗi thiếu thư viện khi khởi động.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp
                    )
                    Button(
                        onClick = { vm.runSmartAutoOptimization() },
                        enabled = !serverRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = Mint),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✨ Tự động bật lại Thư viện Lõi & Tối ưu ngay", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IndigoSoft)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "⚡ Thao tác nhanh toàn bộ Mod (Có hộp thoại cảnh báo an toàn):",
                    color = Indigo,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { pendingBulkModAction = "disable_all" },
                        enabled = !serverRunning && enabledModsCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                        shape = McShape.ControlCompact,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "🔴 Tắt toàn bộ Mod ($enabledModsCount)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Button(
                        onClick = { pendingBulkModAction = "enable_all" },
                        enabled = !serverRunning && disabledModsCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Mint),
                        shape = McShape.ControlCompact,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "🟢 Bật toàn bộ Mod ($disabledModsCount)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                OutlinedButton(
                    onClick = { pendingBulkModAction = "optimize_only" },
                    enabled = !serverRunning && mods.isNotEmpty(),
                    shape = McShape.ControlCompact,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Indigo),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "🤖 Chế độ Thông Minh: Chỉ bật Mod Tối ưu TPS/RAM & Thư viện Lõi",
                        color = Indigo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Thanh tìm kiếm & Bộ lọc Tất cả / Đang bật / Đang tắt
            OutlinedTextField(
                value = installedModSearch,
                onValueChange = { installedModSearch = it },
                placeholder = { Text("🔍 Tìm Mod đã cài (VD: Lithium, Macaw, Farmer, Fabric API...)", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = McShape.ControlCompact
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    InstalledFilter.All to "📦 Tất cả (${mods.size})",
                    InstalledFilter.Enabled to "🟢 Đang bật ($enabledModsCount)",
                    InstalledFilter.Disabled to "⏸ Đang tắt ($disabledModsCount)"
                ).forEach { (filter, label) ->
                    val isSelected = installedModFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Indigo else IndigoSoft)
                            .clickable { installedModFilter = filter }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            label,
                            color = if (isSelected) Color.White else Indigo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredInstalledMods, key = { it.fileName }) { mod ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (mod.isEnabled) MintSoft else CoralSoft.copy(alpha = 0.45f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AddonJarIcon(
                            jarPath = mod.filePath,
                            baseName = mod.baseName,
                            slugHint = mod.modId ?: mod.baseName,
                            modIdHint = mod.modId,
                            isEnabled = mod.isEnabled,
                            size = 38.dp
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val titleText = mod.displayName?.takeIf { it.isNotBlank() } ?: mod.baseName
                            Text(
                                titleText,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val statusText = if (mod.isEnabled) "🟢 Đang bật" else "⏸ Đang tắt (.disabled)"
                            val fileSub = if (mod.displayName != null && !mod.displayName.equals(mod.baseName, ignoreCase = true)) {
                                " · ${mod.baseName}"
                            } else ""
                            Text(
                                "$statusText · 💾 ${mod.sizeText}$fileSub",
                                color = if (mod.isEnabled) Mint else Coral,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            AddonInfoLines(
                                rememberModInfo(mod),
                                modifier = Modifier.padding(top = 3.dp),
                                descMaxLines = 3,
                                warnClientOnlyOnServer = mod.isEnabled
                            )
                        }
                        // Công tắc Bật / Tắt có cảnh báo xác nhận
                        Switch(
                            checked = mod.isEnabled,
                            onCheckedChange = { pendingModToggle = mod },
                            enabled = !serverRunning,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        TextButton(
                            onClick = { pendingModToggle = mod },
                            enabled = !serverRunning
                        ) {
                            Text(
                                if (mod.isEnabled) "⏸ Tắt Mod" else "🟢 Bật Mod",
                                color = if (mod.isEnabled) Coral else Mint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { onDelete(mod) }, enabled = !serverRunning, modifier = Modifier.size(McIcon.Touch)) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.s339), tint = Coral, modifier = Modifier.size(McIcon.Action))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { filePickerLauncher.launch(arrayOf("application/java-archive", "*/*")) },
            enabled = activeCoreExists,
            shape = McShape.ControlCompact,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.s830), color = Indigo, fontSize = 12.sp)
        }
    }

    // Hộp thoại Cảnh báo xác nhận trước khi Bật / Tắt Mod
    pendingModToggle?.let { targetMod ->
        val willEnable = !targetMod.isEnabled
        AlertDialog(
            onDismissRequest = { pendingModToggle = null },
            title = {
                Text(
                    if (willEnable) "🟢 Xác nhận BẬT Mod '${targetMod.baseName}'?"
                    else "⚠️ Cảnh báo trước khi TẮT Mod '${targetMod.baseName}'?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (willEnable) {
                            "Bạn sắp BẬT lại mod '${targetMod.baseName}' (${targetMod.sizeText}).\n\n" +
                                "• Hãy đảm bảo máy chủ đã có đủ Thư viện Lõi (Fabric API, Architectury, Cloth Config...) tương thích với phiên bản Minecraft hiện tại.\n" +
                                "• Nếu mod thêm khối/vật phẩm mới, người chơi (Client) cũng cần có mod này trong máy."
                        } else {
                            "Bạn sắp TẮT mod '${targetMod.baseName}' (${targetMod.sizeText} → đổi đuôi thành .jar.disabled).\n\n" +
                                "⚠️ LƯU Ý QUAN TRỌNG:\n" +
                                "1️⃣ Nếu đây là Mod Thư viện Lõi (như Fabric API, Architectury, Balm, Puzzles Lib, Cloth Config...), việc tắt nó sẽ làm các Mod phụ thuộc báo lỗi khi bật Server!\n" +
                                "2️⃣ Nếu đây là Mod thêm Khối / Nội thất / Ẩm thực / Sinh vật, các khối của mod này đã đặt trong Map có thể bị mất nếu bạn mở Map khi mod đang tắt."
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fileName = targetMod.fileName
                        pendingModToggle = null
                        onToggle(fileName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (willEnable) Mint else Coral)
                ) {
                    Text(
                        if (willEnable) "🟢 Đồng ý Bật Mod" else "⏸ Đồng ý Tắt Mod",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingModToggle = null }) {
                    Text("Hủy", color = Muted)
                }
            }
        )
    }

    // Hộp thoại Cảnh báo xác nhận trước khi Tắt / Bật TOÀN BỘ Mod
    pendingBulkModAction?.let { bulkAction ->
        AlertDialog(
            onDismissRequest = { pendingBulkModAction = null },
            title = {
                Text(
                    when (bulkAction) {
                        "disable_all" -> "⚠️ Cảnh báo trước khi TẮT TOÀN BỘ MOD?"
                        "enable_all" -> "🟢 Xác nhận BẬT LẠI TOÀN BỘ MOD?"
                        else -> "🤖 Chế độ Thông Minh: Chỉ Bật Mod Tối Ưu & Lõi?"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when (bulkAction) {
                            "disable_all" ->
                                "Bạn sắp TẮT TOÀN BỘ $enabledModsCount Mod đang bật trong thư mục mods/ (tất cả sẽ được đổi đuôi thành .jar.disabled, KHÔNG bị xóa khỏi máy).\n\n" +
                                    "💡 Khi nào nên dùng:\n" +
                                    "• Khi bạn muốn kiểm tra lỗi Crash hoặc muốn Server khởi động siêu nhanh như bản gốc.\n" +
                                    "• Khi muốn cho bạn bè dùng điện thoại Minecraft PE (qua Geyser) hoặc máy không cài Mod vào chơi.\n\n" +
                                    "⚠️ LƯU Ý QUAN TRỌNG: Nếu Map hiện tại đã đặt sẵn các khối Nội thất / Ẩm thực của Mod, hãy bấm '📦 Sao lưu Mod cho Map này' hoặc tạo Map mới trước khi mở Server không có Mod!"
                            "enable_all" ->
                                "Bạn sắp BẬT LẠI TOÀN BỘ ${mods.size} Mod trong thư mục mods/.\n\n" +
                                    "• Tất cả các tệp .jar.disabled sẽ được khôi phục về .jar để nạp trong lần bật Server tiếp theo."
                            else ->
                                "Trợ lý Thông Minh sẽ CHỈ GIỮ LẠI các Mod Tối ưu TPS/RAM (Lithium, FerriteCore, C2ME, ModernFix, Krypton, ServerCore...) & Thư viện Lõi (Fabric API, Architectury...), đồng thời tạm tắt các Mod thêm khối 3D nặng.\n\n" +
                                    "✨ Giúp máy yếu chạy siêu mượt 20 TPS và cho phép cả người chơi không cài Mod vào Server!"
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val action = bulkAction
                        pendingBulkModAction = null
                        when (action) {
                            "disable_all" -> vm.setAllModsEnabled(false)
                            "enable_all" -> vm.setAllModsEnabled(true)
                            else -> vm.enableOnlyCoreAndOptimizationMods()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (bulkAction) {
                            "disable_all" -> Coral
                            "enable_all" -> Mint
                            else -> Indigo
                        }
                    )
                ) {
                    Text(
                        when (bulkAction) {
                            "disable_all" -> "🔴 Đồng ý Tắt Toàn Bộ Mod"
                            "enable_all" -> "🟢 Đồng ý Bật Toàn Bộ Mod"
                            else -> "⚡ Áp Dụng Chế Độ Tối Ưu"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkModAction = null }) {
                    Text("Hủy", color = Muted)
                }
            }
        )
    }


    // ── Kho Modrinth Toàn Cầu (Mọi Mod & Phiên bản thông dụng) ──
    var sortMenuOpen by remember { mutableStateOf(false) }
    val universalModCategories = remember {
        listOf(
            "" to "🔥 Phổ biến nhất",
            "optimization" to "⚡ Tối ưu",
            "worldgen" to "🌍 Thế giới",
            "equipment" to "⚔️ Vũ khí & Giáp",
            "mobs" to "🐾 Sinh vật",
            "decoration" to "🏠 Nội thất",
            "food" to "🍳 Ẩm thực",
            "technology" to "⚙️ Công nghệ",
            "magic" to "🪄 Phép thuật",
            "storage" to "🎒 Lưu trữ",
            "library" to "🧱 Thư viện"
        )
    }
    McCard(title = "🌐 Kho Modrinth Toàn Cầu (Tìm & Cài Mọi Mod)") {
        Text(
            "Tìm kiếm bất kỳ Mod nào trên Modrinth hoặc bấm danh mục bên dưới để duyệt kho Mod thông dụng. Khi bấm Cài đặt, App sẽ tự chọn đúng phiên bản tương thích và tự tải kèm toàn bộ Mod phụ thuộc (Fabric API, Architectury, Balm, GeckoLib...).",
            color = Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))
        // 搜索框 + 排序 + 搜索按钮
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = modrinthQuery,
                onValueChange = { modrinthQuery = it },
                placeholder = { Text(stringResource(R.string.s833), fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = McShape.ControlCompact
            )
            Spacer(Modifier.size(6.dp))
            Box {
                OutlinedButton(
                    onClick = { sortMenuOpen = true },
                    shape = McShape.ControlCompact
                ) {
                    Text(sortOptions[sortIndex].second, fontSize = 11.sp, color = Indigo)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    sortOptions.forEachIndexed { i, (_, label) ->
                        DropdownMenuItem(
                            text = { Text(label, fontSize = 12.sp) },
                            onClick = {
                                sortIndex = i
                                sortMenuOpen = false
                                onSearchModrinth(
                                    modrinthQuery.trim(),
                                    selectedLoaders.toList(),
                                    sortOptions[i].first,
                                    selectedModVersion,
                                    selectedModCategory
                                )
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.size(6.dp))
            Button(
                onClick = {
                    onSearchModrinth(
                        modrinthQuery.trim(),
                        selectedLoaders.toList(),
                        sortOptions[sortIndex].first,
                        selectedModVersion,
                        selectedModCategory
                    )
                },
                enabled = true,
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                shape = McShape.ControlCompact
            ) {
                Text(stringResource(R.string.s834), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            universalModCategories.forEach { (catKey, catLabel) ->
                FilterChip(
                    selected = selectedModCategory == catKey,
                    onClick = {
                        selectedModCategory = catKey
                        onSearchModrinth(
                            modrinthQuery.trim(),
                            selectedLoaders.toList(),
                            sortOptions[sortIndex].first,
                            selectedModVersion,
                            catKey
                        )
                    },
                    label = { Text(catLabel, fontSize = 11.sp) }
                )
            }
        }
        // 版本筛选 ｜ 加载器 下拉（布局参考：版本下拉框｜加载器下拉框）
        var versionMenuOpen by remember { mutableStateOf(false) }
        var versionSearchInput by remember { mutableStateOf("") }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.s1061), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(6.dp))
            OutlinedButton(
                onClick = { versionMenuOpen = true },
                shape = McShape.ControlCompact
            ) {
                Text(
                    if (selectedModVersion.isNotBlank()) "📋 ${selectedModVersion} ▾" else "📋 Chọn / Nhập phiên bản ▾",
                    fontSize = 11.sp,
                    color = Indigo,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.s835), color = Muted, fontSize = 10.sp)
        }
        if (versionMenuOpen) {
            val trimmedVerSearch = versionSearchInput.trim()
            val filteredModVersions = remember(modrinthGameVersions, trimmedVerSearch) {
                if (trimmedVerSearch.isBlank()) modrinthGameVersions
                else modrinthGameVersions.filter { it.contains(trimmedVerSearch, ignoreCase = true) }
            }
            AlertDialog(
                onDismissRequest = { versionMenuOpen = false },
                title = {
                    Text(
                        "🎮 Lướt chọn hoặc nhập tay phiên bản Game",
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
                            value = versionSearchInput,
                            onValueChange = { versionSearchInput = it },
                            placeholder = { Text("🔍 Nhập tìm hoặc gõ tay (VD: 1.21.4, 1.20.1, 1.16.5...)", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (versionSearchInput.isNotEmpty()) {
                                    Text(
                                        "✕",
                                        color = Muted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { versionSearchInput = "" }
                                            .padding(8.dp)
                                    )
                                }
                            }
                        )
                        if (trimmedVerSearch.isNotBlank() && filteredModVersions.none { it.equals(trimmedVerSearch, ignoreCase = true) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Mint.copy(alpha = 0.14f))
                                    .border(1.dp, Mint.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSetModVersion(trimmedVerSearch)
                                        versionMenuOpen = false
                                        onSearchModrinth(
                                            modrinthQuery.trim(),
                                            selectedLoaders.toList(),
                                            sortOptions[sortIndex].first,
                                            trimmedVerSearch,
                                            selectedModCategory
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "✨ Tìm Mod/Plugin cho bản nhập tay: $trimmedVerSearch",
                                    color = Mint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "Chọn luôn",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Mint)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            items(
                                items = filteredModVersions,
                                key = { "mod_ver_$it" }
                            ) { v ->
                                val isCurrent = v == selectedModVersion
                                val isServerVer = v == currentServerVersion
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isCurrent -> Indigo.copy(alpha = 0.20f)
                                                isServerVer -> Mint.copy(alpha = 0.12f)
                                                else -> com.mineserve.mobile.ui.theme.FieldGray
                                            }
                                        )
                                        .clickable {
                                            onSetModVersion(v)
                                            versionMenuOpen = false
                                            onSearchModrinth(
                                                modrinthQuery.trim(),
                                                selectedLoaders.toList(),
                                                sortOptions[sortIndex].first,
                                                v,
                                                selectedModCategory
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (isServerVer) "🎯 Minecraft $v (Phiên bản Server hiện tại)"
                                            else "📦 Minecraft $v",
                                            color = if (isCurrent) Indigo else if (isServerVer) Mint else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Text(
                                        if (isCurrent) "✓ Đang chọn" else "Chọn & Tìm",
                                        color = if (isCurrent) Indigo else Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCurrent) Indigo.copy(alpha = 0.15f) else Indigo)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { versionMenuOpen = false }) {
                        Text("Đóng", color = Indigo, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        if (selectedModVersion.isNotBlank() && selectedModVersion != currentServerVersion && currentServerVersion.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.s1063, selectedModVersion, currentServerVersion),
                color = Coral,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        // 加载器多选筛选（可展开列表，避免元素零散排布）
        if (modrinthLoaders.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LoaderFilterSection(
                loaders = modrinthLoaders,
                selected = selectedLoaders,
                onToggle = { loader ->
                    selectedLoaders = if (loader in selectedLoaders) selectedLoaders - loader else selectedLoaders + loader
                }
            )
        }
        // 结果列表
        if (modrinthResults.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val coreLoader = if (coreType == ServerCore.Fabric || coreType == ServerCore.Quilt) "fabric" else "forge"
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
            ) {
                itemsIndexed(modrinthResults, key = { index, hit -> "${hit.slug}:$index" }) { _, hit ->
                val slugLower = hit.slug.lowercase(java.util.Locale.ROOT)
                val slugNorm = slugLower.replace("-", "").replace("_", "")
                val titleNorm = hit.title.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
                val matchedInstalledMod = mods.firstOrNull { m ->
                    val baseLower = m.baseName.lowercase(java.util.Locale.ROOT)
                    val baseNorm = baseLower.replace("-", "").replace("_", "")
                    baseLower.startsWith(slugLower) ||
                        baseLower.contains(slugLower) ||
                        (slugNorm.length >= 4 && baseNorm.startsWith(slugNorm)) ||
                        (titleNorm.length >= 5 && baseNorm.startsWith(titleNorm))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable { detailHit = hit },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AddonIconLoader.registerKnownIcon(hit.slug, hit.title, hit.icon_url)
                    ModrinthIcon(hit.icon_url)
                    Spacer(Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hit.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val downloadsText = formatModrinthDownloads(hit.downloads)
                        Text(
                            "${hit.author} · $downloadsText",
                            color = Muted,
                            fontSize = 10.sp
                        )
                        // 当前核心不匹配红字提醒
                        if (hit.categories.isNotEmpty() && coreLoader !in hit.categories) {
                            Text(
                                stringResource(R.string.s836, coreType.displayName),
                                color = Coral,
                                fontSize = 9.sp
                            )
                        }
                        // 选中版本与当前服务器版本不匹配红字提示（模组条目下方）
                        if (selectedModVersion.isNotBlank() && currentServerVersion.isNotBlank() && selectedModVersion != currentServerVersion) {
                            Text(
                                stringResource(R.string.s1063, selectedModVersion, currentServerVersion),
                                color = Coral,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        AddonInfoLines(rememberHitInfo(hit, isPlugin = false), modifier = Modifier.padding(top = 2.dp), descMaxLines = 2)
                    }
                    if (matchedInstalledMod != null) {
                        if (matchedInstalledMod.isEnabled) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MintSoft)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("✅ Đã cài", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onToggle(matchedInstalledMod.fileName) },
                                enabled = !serverRunning,
                                shape = McShape.ControlCompact
                            ) {
                                Text("⏸ Đã tải · Bật", color = Indigo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = { onInstallModrinth(hit, selectedModVersion) },
                            colors = ButtonDefaults.buttonColors(containerColor = Mint),
                            shape = McShape.ControlCompact
                        ) {
                            Text(stringResource(R.string.s837), color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onLoadMoreModrinth,
                enabled = !isLoadingMore,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) { Text(if (isLoadingMore) "Đang tải..." else stringResource(R.string.pl_load_more), color = Indigo, fontSize = 11.sp) }
        }
    }

    // 模组详情对话框
    ModrinthDetailDialog(detailHit) { detailHit = null }
}

/** 插件页 Modrinth 资源检索（布局与模组页统一） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginModrinthCard(
    results: List<PluginManager.ModrinthHit>,
    installedPlugins: List<PluginManager.InstalledPlugin>,
    loaders: List<String>,
    gameVersions: List<String>,
    selectedVersion: String,
    currentServerVersion: String,
    onSetVersion: (String) -> Unit,
    onSearch: (String, List<String>, String, String, String) -> Unit,
    onInstall: (PluginManager.ModrinthHit, String) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean
) {
    var query by remember { mutableStateOf("") }
    var selectedPluginCategory by remember { mutableStateOf("") }
    var selectedLoaders by remember { mutableStateOf(setOf<String>()) }
    var detailHit by remember { mutableStateOf<PluginManager.ModrinthHit?>(null) }
    var sortIndex by remember { mutableStateOf(0) }
    val sortOptions = listOf("downloads" to stringResource(R.string.s825), "relevance" to stringResource(R.string.s826), "newest" to stringResource(R.string.s827))
    var sortMenuOpen by remember { mutableStateOf(false) }
    var versionMenuOpen by remember { mutableStateOf(false) }
    val universalPluginCategories = remember {
        listOf(
            "" to "🔥 Phổ biến nhất",
            "optimization" to "⚡ Tối ưu",
            "management" to "🛡️ Quản trị",
            "economy" to "💰 Kinh tế",
            "worldgen" to "🌍 Thế giới",
            "minigame" to "🎮 Minigame",
            "social" to "💬 Chat & Giao diện",
            "utility" to "🔧 Tiện ích"
        )
    }
    LaunchedEffect(selectedVersion) {
        if (results.isEmpty()) {
            onSearch("", selectedLoaders.toList(), "downloads", selectedVersion.ifBlank { currentServerVersion }, selectedPluginCategory)
        }
    }
    // 插件页统一使用 Modrinth 插件库（project_type=plugin，加载器以 bukkit/paper 为主）
    val coreLoader = "bukkit"

    McCard(title = "🌐 Kho Plugin Modrinth Toàn Cầu (Mọi Plugin & Phiên Bản)") {
        Text(
            "Tìm kiếm bất kỳ Plugin nào trên Modrinth hoặc duyệt theo danh mục bên dưới. App tự động chọn phiên bản tương thích và tải kèm các Plugin phụ thuộc cần thiết.",
            color = Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))
        // 搜索框 + 排序 + 搜索按钮
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.s1070), fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = McShape.ControlCompact
            )
            Spacer(Modifier.size(6.dp))
            Box {
                OutlinedButton(
                    onClick = { sortMenuOpen = true },
                    shape = McShape.ControlCompact
                ) {
                    Text(sortOptions[sortIndex].second, fontSize = 11.sp, color = Indigo)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    sortOptions.forEachIndexed { i, (_, label) ->
                        DropdownMenuItem(
                            text = { Text(label, fontSize = 12.sp) },
                            onClick = {
                                sortIndex = i
                                sortMenuOpen = false
                                onSearch(query.trim(), selectedLoaders.toList(), sortOptions[i].first, selectedVersion, selectedPluginCategory)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.size(6.dp))
            Button(
                onClick = { onSearch(query.trim(), selectedLoaders.toList(), sortOptions[sortIndex].first, selectedVersion, selectedPluginCategory) },
                enabled = true,
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                shape = McShape.ControlCompact
            ) {
                Text(stringResource(R.string.s834), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            universalPluginCategories.forEach { (catKey, catLabel) ->
                FilterChip(
                    selected = selectedPluginCategory == catKey,
                    onClick = {
                        selectedPluginCategory = catKey
                        onSearch(
                            query.trim(),
                            selectedLoaders.toList(),
                            sortOptions[sortIndex].first,
                            selectedVersion,
                            catKey
                        )
                    },
                    label = { Text(catLabel, fontSize = 11.sp) }
                )
            }
        }
        // 版本筛选 ｜ 加载器 下拉
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.s1061), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(6.dp))
            Box {
                OutlinedButton(
                    onClick = { versionMenuOpen = true },
                    shape = McShape.ControlCompact
                ) {
                    Text(
                        if (selectedVersion.isNotBlank()) selectedVersion else "--",
                        fontSize = 11.sp,
                        color = Indigo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(expanded = versionMenuOpen, onDismissRequest = { versionMenuOpen = false }) {
                    if (gameVersions.isEmpty()) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.s1062), fontSize = 11.sp) }, onClick = { versionMenuOpen = false })
                    }
                    gameVersions.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v, fontSize = 11.sp, color = if (v == currentServerVersion) Indigo else Muted) },
                            onClick = { onSetVersion(v); versionMenuOpen = false }
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.s835), color = Muted, fontSize = 10.sp)
        }
        if (selectedVersion.isNotBlank() && currentServerVersion.isNotBlank() && selectedVersion != currentServerVersion) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.s1063, selectedVersion, currentServerVersion),
                color = Coral,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        // 加载器多选筛选（可展开列表，避免元素零散排布）
        if (loaders.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LoaderFilterSection(
                loaders = loaders,
                selected = selectedLoaders,
                onToggle = { loader ->
                    selectedLoaders = if (loader in selectedLoaders) selectedLoaders - loader else selectedLoaders + loader
                }
            )
        }
        // 结果列表（条目下方红字提示版本不匹配）
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
            ) {
                itemsIndexed(results, key = { index, hit -> "${hit.slug}:$index" }) { _, hit ->
                val slugLower = hit.slug.lowercase(java.util.Locale.ROOT)
                val titleNorm = hit.title.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
                val matchedInstalledPlugin = installedPlugins.firstOrNull { p ->
                    val nameLower = (p.meta?.name ?: p.baseName).lowercase(java.util.Locale.ROOT)
                    val fileLower = p.fileName.lowercase(java.util.Locale.ROOT)
                    val nameNorm = nameLower.replace(Regex("[^a-z0-9]"), "")
                    nameLower == slugLower ||
                        fileLower.startsWith(slugLower) ||
                        (titleNorm.length >= 4 && nameNorm == titleNorm)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable { detailHit = hit },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AddonIconLoader.registerKnownIcon(hit.slug, hit.title, hit.icon_url)
                    ModrinthIcon(hit.icon_url)
                    Spacer(Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hit.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val downloadsText = formatModrinthDownloads(hit.downloads)
                        Text(
                            "${hit.author} · $downloadsText",
                            color = Muted,
                            fontSize = 10.sp
                        )
                        if (hit.categories.isNotEmpty() && coreLoader !in hit.categories) {
                            Text(
                                stringResource(R.string.s1071, coreLoader),
                                color = Coral,
                                fontSize = 9.sp
                            )
                        }
                        if (selectedVersion.isNotBlank() && currentServerVersion.isNotBlank() && selectedVersion != currentServerVersion) {
                            Text(
                                stringResource(R.string.s1063, selectedVersion, currentServerVersion),
                                color = Coral,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        AddonInfoLines(rememberHitInfo(hit, isPlugin = true), modifier = Modifier.padding(top = 2.dp), descMaxLines = 2)
                    }
                    if (matchedInstalledPlugin != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintSoft)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (matchedInstalledPlugin.isEnabled) "✅ Đã cài" else "⏸ Đã tải",
                                color = Mint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = { onInstall(hit, selectedVersion) },
                            colors = ButtonDefaults.buttonColors(containerColor = Mint),
                            shape = McShape.ControlCompact
                        ) {
                            Text(stringResource(R.string.s837), color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onLoadMore,
                enabled = !isLoadingMore,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) { Text(if (isLoadingMore) "Đang tải..." else stringResource(R.string.pl_load_more), color = Indigo, fontSize = 11.sp) }
        }
    }

    // 插件详情对话框（Modrinth 搜索结果）
    ModrinthDetailDialog(detailHit, isPlugin = true) { detailHit = null }
}

/** Modrinth 下载量格式化 */
@Composable
private fun formatModrinthDownloads(d: Long): String = when {
    d >= 1_000_000 -> String.format(stringResource(R.string.s838), d / 1_000_000.0)
    d >= 1_000 -> String.format(stringResource(R.string.s839), d / 1_000.0)
    else -> stringResource(R.string.s840, d)
}

/** Modrinth 模组图标（网络加载，失败显示占位） */
/** 可展开的加载器筛选列表：默认收起（显示标题+已选数量），点击展开多选，避免元素零散排布 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoaderFilterSection(
    loaders: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(FieldGray)
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.s835),
                color = Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (selected.isNotEmpty()) {
                Text(
                    stringResource(R.string.ui_loader_selected, selected.size),
                    color = Indigo,
                    fontSize = 10.sp
                )
                Spacer(Modifier.size(6.dp))
            }
            Text(if (expanded) "▾" else "▸", color = Muted, fontSize = 11.sp)
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                loaders.forEach { loader ->
                    val isSelected = loader in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggle(loader) },
                        label = { Text(loader, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IndigoSoft,
                            selectedLabelColor = Indigo
                        )
                    )
                }
            }
        }
    }
}

/** Modrinth 搜索结果详情对话框（模组/插件通用）：作者/下载量/分类/描述 */
@Composable
private fun ModrinthDetailDialog(hit: PluginManager.ModrinthHit?, isPlugin: Boolean = false, onDismiss: () -> Unit) {
    val h = hit ?: return
    val info = rememberHitInfo(h, isPlugin)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModrinthIcon(h.icon_url, size = 30.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    h.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "${h.author} · ${formatModrinthDownloads(h.downloads)}",
                    color = Muted,
                    fontSize = 12.sp
                )
                if (h.categories.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        h.categories.joinToString(" · "),
                        color = Indigo,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                AddonInfoLines(info, descMaxLines = 30, showSideExplain = true)
                if (h.description.isNotBlank() && info.descVi != h.description) {
                    Spacer(Modifier.height(8.dp))
                    Text("Mô tả gốc (tiếng Anh):", fontSize = 10.sp, color = Muted)
                    Text(
                        h.description,
                        fontSize = 11.sp,
                        color = Muted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.s620))
            }
        }
    )
}

@Composable
private fun ModrinthIcon(url: String, size: Dp = 32.dp) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = AddonIconLoader.load(url)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(IndigoSoft),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("🟦", fontSize = 14.sp)
        }
    }
}

@Composable
private fun AddonJarIcon(
    jarPath: String? = null,
    baseName: String,
    slugHint: String? = null,
    modIdHint: String? = null,
    iconUrlHint: String? = null,
    isEnabled: Boolean = true,
    size: Dp = 36.dp
) {
    var bitmap by remember(jarPath, baseName, slugHint, modIdHint, iconUrlHint) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(jarPath, baseName, slugHint, modIdHint, iconUrlHint) {
        bitmap = AddonIconLoader.loadForAddon(
            jarPath = jarPath,
            baseName = baseName,
            slugHint = slugHint,
            modIdHint = modIdHint,
            iconUrlHint = iconUrlHint
        )
    }
    val borderColor = if (isEnabled) Mint.copy(alpha = 0.65f) else Coral.copy(alpha = 0.65f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isEnabled) IndigoSoft else CoralSoft)
            .border(1.dp, borderColor, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = baseName,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            )
        } else {
            val initials = remember(baseName) {
                baseName.removePrefix("-").take(2).uppercase()
            }
            Text(
                initials,
                color = if (isEnabled) Indigo else Coral,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/** 字节数格式化（进度对话框使用） */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModPackDecoderAndTransferCard(
    vm: McViewModel,
    activeCoreExists: Boolean,
    serverRunning: Boolean,
    hasInstalledMods: Boolean,
    showFull56ListByDefault: Boolean = true
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val installedMods by vm.mods.collectAsStateWithLifecycle()
    val all56Items = remember { vm.userScreenshot56ModItems() }
    val categoryGroups = remember { vm.modPackCategoryGroups() }
    val multiVersionPresets = remember { vm.multiVersionPresets() }
    val serverMcVer = vm.currentServerVersionForUi()

    val otherServers = remember(config.installedCores, config.activeCoreName) {
        config.installedCores.filter { it.name != config.activeCoreName }
    }
    var showServerTransferDialog by remember { mutableStateOf(false) }
    var showAutoPackConfirmDialog by remember { mutableStateOf(false) }
    var showFull56List by remember(showFull56ListByDefault) { mutableStateOf(showFull56ListByDefault) }
    var copyConfigsToo by remember { mutableStateOf(true) }
    var pendingCatalogModToggle by remember { mutableStateOf<Pair<PluginManager.UserScreenshotModItem, PluginManager.ModEntry>?>(null) }

    // Bộ lọc Nhóm phân loại ("ALL" hoặc 1 trong 6 nhóm) & Phiên bản Minecraft
    var selectedCategoryGroup by remember { mutableStateOf("ALL") }
    var selectedCustomVersion by remember { mutableStateOf("") } // "" = Tự khớp phiên bản Server
    var catalogSearchQuery by remember { mutableStateOf("") }

    val effectiveMcVer = selectedCustomVersion.ifBlank { serverMcVer }

    fun findInstalledModFor56(item: PluginManager.UserScreenshotModItem): PluginManager.ModEntry? {
        val slugLower = item.slug.lowercase(java.util.Locale.ROOT)
        val slugNorm = slugLower.replace("-", "").replace("_", "")
        val jarPrefix = item.originalJarName
            .substringBefore('-')
            .substringBefore('_')
            .lowercase(java.util.Locale.ROOT)
        val jarNorm = jarPrefix.replace("-", "").replace("_", "")
        return installedMods.firstOrNull { mod ->
            val baseLower = mod.baseName.lowercase(java.util.Locale.ROOT)
            val baseNorm = baseLower.replace("-", "").replace("_", "")
            baseLower.startsWith(slugLower) ||
                (jarPrefix.length >= 3 && baseLower.startsWith(jarPrefix)) ||
                baseLower.contains(slugLower) ||
                (slugNorm.length >= 4 && baseNorm.startsWith(slugNorm)) ||
                (jarNorm.length >= 4 && baseNorm.startsWith(jarNorm))
        }
    }

    val matchedModBySlug = remember(installedMods, all56Items) {
        all56Items.associate { item -> item.slug to findInstalledModFor56(item) }
    }
    val downloaded56Count = remember(matchedModBySlug) {
        matchedModBySlug.values.count { it != null }
    }
    val enabled56Count = remember(matchedModBySlug) {
        matchedModBySlug.values.count { it?.isEnabled == true }
    }
    val groupCountsByTitle = remember(all56Items, categoryGroups, matchedModBySlug) {
        categoryGroups.associateWith { grp ->
            val total = all56Items.count { it.categoryGroup == grp }
            val inst = all56Items.count { it.categoryGroup == grp && matchedModBySlug[it.slug] != null }
            inst to total
        }
    }
    val matched56Count = downloaded56Count

    val filteredCatalogItems = remember(all56Items, selectedCategoryGroup, catalogSearchQuery) {
        all56Items.filter { item ->
            val matchGroup = selectedCategoryGroup == "ALL" || item.categoryGroup == selectedCategoryGroup
            val q = catalogSearchQuery.trim()
            val matchQuery = q.isEmpty() ||
                item.title.contains(q, ignoreCase = true) ||
                item.descVi.contains(q, ignoreCase = true) ||
                item.category.contains(q, ignoreCase = true) ||
                item.originalJarName.contains(q, ignoreCase = true)
            matchGroup && matchQuery
        }
    }

    // 1. Chọn nguyên Thư mục Full Mod (SAF OpenDocumentTree)
    val folderTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            vm.importModsFromFolderUri(treeUri)
        }
    }

    // 2. Chọn gói nén .zip / .mrpack
    val archiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { archiveUri ->
        if (archiveUri != null) {
            vm.importModsFromArchiveUri(archiveUri)
        }
    }

    // 3. Chọn nhiều tệp .jar cùng lúc
    val multiJarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.uploadMultipleModFiles(uris)
        }
    }

    // 4. Xuất toàn bộ mods/ ra .zip
    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { targetUri ->
        if (targetUri != null) {
            vm.exportCurrentModsToZip(targetUri, includeConfigs = true)
        }
    }

    McCard(
        title = "📦 Kho 56 Mod Gốc (Phân 6 Nhóm & Đa Phiên Bản) + Giải Mã Gói Mod",
        trailing = {
            Text(
                if (downloaded56Count > 0) "✅ Đã cài $downloaded56Count/${all56Items.size} mod" else "Chưa cài (0/${all56Items.size})",
                color = if (downloaded56Count > 0) Mint else Indigo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    ) {
        Text(
            "Đã tích hợp và PHÂN LOẠI ĐỦ 56 MOD từ 5 ảnh chụp màn hình của bạn thành 6 nhóm chức năng rõ ràng (hỗ trợ đa phiên bản Minecraft: 1.21.11, 1.21.4, 1.21.1, 1.20.4, 1.20.1, 1.19.2, 1.18.2, 1.16.5...).\n" +
                "• Những mod nào đã tải sẽ hiển thị rõ '✅ Đã cài' và tự động bỏ qua khi tải lại để không bao giờ bị tải trùng!",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))

        // ── Thanh chọn Phiên bản Minecraft (Đa phiên bản: Lướt chọn hoặc Nhập tay) ──
        var showPackVerPickerModal by remember { mutableStateOf(false) }
        var packVerSearchInput by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Indigo.copy(alpha = 0.08f))
                .border(1.dp, Indigo.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                .clickable { showPackVerPickerModal = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "🌐 Phiên bản tải Mod: MC $effectiveMcVer" +
                        if (selectedCustomVersion.isBlank()) " (Theo Server)" else " (Đã chọn riêng)",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Indigo
                )
                Text(
                    "Ấn vào đây để lướt chọn hoặc nhập tay phiên bản rồi cài Mod",
                    fontSize = 10.sp,
                    color = Muted
                )
            }
            Text(
                "📋 Lướt chọn / Nhập ▾",
                color = Indigo,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Indigo.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        if (showPackVerPickerModal) {
            val trimmedPackVer = packVerSearchInput.trim()
            val filteredPresets = remember(multiVersionPresets, trimmedPackVer) {
                if (trimmedPackVer.isBlank()) multiVersionPresets
                else multiVersionPresets.filter { it.contains(trimmedPackVer, ignoreCase = true) }
            }
            AlertDialog(
                onDismissRequest = { showPackVerPickerModal = false },
                title = {
                    Text(
                        "🌐 Lướt chọn hoặc nhập tay phiên bản tải Mod",
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
                            value = packVerSearchInput,
                            onValueChange = { packVerSearchInput = it },
                            placeholder = { Text("🔍 Nhập tìm hoặc gõ tay (VD: 1.21.4, 1.20.1, 1.16.5...)", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (packVerSearchInput.isNotEmpty()) {
                                    Text(
                                        "✕",
                                        color = Muted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { packVerSearchInput = "" }
                                            .padding(8.dp)
                                    )
                                }
                            }
                        )
                        if (trimmedPackVer.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Mint.copy(alpha = 0.14f))
                                    .border(1.dp, Mint.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedCustomVersion = trimmedPackVer
                                        showPackVerPickerModal = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "✨ Dùng phiên bản nhập tay: MC $trimmedPackVer",
                                    color = Mint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "✓ Chọn",
                                        color = Indigo,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Indigo.copy(alpha = 0.14f))
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                    if (activeCoreExists && !serverRunning) {
                                        Text(
                                            "⬇️ Cài 56 Mod",
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Mint)
                                                .clickable {
                                                    selectedCustomVersion = trimmedPackVer
                                                    showPackVerPickerModal = false
                                                    showAutoPackConfirmDialog = true
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 330.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            item(key = "auto_server_ver") {
                                val isAutoServerVer = selectedCustomVersion.isBlank()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isAutoServerVer) Mint.copy(alpha = 0.18f) else FieldGray)
                                        .clickable {
                                            selectedCustomVersion = ""
                                            showPackVerPickerModal = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🎯 Theo Server hiện tại (MC $serverMcVer)",
                                        color = if (isAutoServerVer) Mint else Indigo,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (isAutoServerVer) "✓ Đang chọn" else "Chọn",
                                        color = if (isAutoServerVer) Mint else Indigo,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            items(filteredPresets, key = { "preset_pack_$it" }) { ver ->
                                val isSelected = selectedCustomVersion == ver
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Indigo.copy(alpha = 0.20f) else FieldGray)
                                        .clickable {
                                            selectedCustomVersion = ver
                                            showPackVerPickerModal = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "📦 Minecraft $ver",
                                        color = if (isSelected) Indigo else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            if (isSelected) "✓ Đang chọn" else "Chọn",
                                            color = Indigo,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Indigo.copy(alpha = 0.14f))
                                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                        )
                                        if (activeCoreExists && !serverRunning) {
                                            Text(
                                                "⬇️ Cài 56 Mod",
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Mint)
                                                    .clickable {
                                                        selectedCustomVersion = ver
                                                        showPackVerPickerModal = false
                                                        showAutoPackConfirmDialog = true
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPackVerPickerModal = false }) {
                        Text("Đóng", color = Indigo, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        // Trạng thái / Nút chính cho trọn bộ 56 Mod
        if (downloaded56Count >= all56Items.size) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MintSoft)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "✅ ĐÃ TẢI & CÀI ĐẶT ĐỦ 56/56 MOD GỐC (MC $effectiveMcVer)",
                    color = Mint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Toàn bộ 56/56 mod trong danh sách của bạn đã có sẵn trong thư mục mods/ (Đang bật: $enabled56Count/56). Bạn không cần tải lại nữa!",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                OutlinedButton(
                    onClick = { showAutoPackConfirmDialog = true },
                    enabled = activeCoreExists && !serverRunning,
                    shape = McShape.ControlCompact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "🔄 Tuỳ chọn: Kiểm tra / Dọn mod lạ / Cài lại",
                        color = Indigo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else if (downloaded56Count > 0) {
            val missingCount = all56Items.size - downloaded56Count
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IndigoSoft)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "📊 Đã tải & cài $downloaded56Count/56 Mod gốc (Còn thiếu $missingCount Mod)",
                    color = Indigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showAutoPackConfirmDialog = true },
                    enabled = activeCoreExists && !serverRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = Mint),
                    shape = McShape.Control,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "➕ Tải BỔ SUNG $missingCount Mod Còn Thiếu (MC $effectiveMcVer)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Button(
                onClick = { showAutoPackConfirmDialog = true },
                enabled = activeCoreExists && !serverRunning,
                colors = ButtonDefaults.buttonColors(containerColor = Mint),
                shape = McShape.Control,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "⚡ Dọn Mod Lạ & Tải ĐỦ 56/56 Mod Gốc (MC $effectiveMcVer)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Hàng 1: Giải mã Thư mục Full Mod & Giải mã Gói ZIP/MRPACK
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { folderTreeLauncher.launch(null) },
                enabled = activeCoreExists && !serverRunning,
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                shape = McShape.ControlCompact,
                modifier = Modifier.weight(1f)
            ) {
                Text("📂 Tải Thư Mục Full Mod", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { archiveLauncher.launch(arrayOf("application/zip", "application/x-modrinth-modpack+zip", "*/*")) },
                enabled = activeCoreExists && !serverRunning,
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                shape = McShape.ControlCompact,
                modifier = Modifier.weight(1f)
            ) {
                Text("🗜️ Giải Mã ZIP / .mrpack", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Hàng 2: Chọn nhiều file .jar cùng lúc & Chuyển từ Server cũ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { multiJarLauncher.launch(arrayOf("application/java-archive", "*/*")) },
                enabled = activeCoreExists && !serverRunning,
                shape = McShape.ControlCompact,
                modifier = Modifier.weight(1f)
            ) {
                Text("📑 Chọn Nhiều Tệp .jar", color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { showServerTransferDialog = true },
                enabled = activeCoreExists && !serverRunning,
                shape = McShape.ControlCompact,
                modifier = Modifier.weight(1f)
            ) {
                Text("🔄 Chuyển Từ Server Cũ", color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Hàng 3: Xuất toàn bộ Mod ra ZIP & Ẩn/Hiện danh sách 56 Mod phân loại
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val safeCore = (config.activeCoreName ?: "server").replace(Regex("[^A-Za-z0-9_-]"), "_")
                    exportZipLauncher.launch("full_mods_${safeCore}.zip")
                },
                enabled = activeCoreExists && hasInstalledMods,
                shape = McShape.ControlCompact,
                modifier = Modifier.weight(1f)
            ) {
                Text("📤 Xuất Full Mod ra ZIP", color = Indigo, fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { showFull56List = !showFull56List },
                shape = McShape.ControlCompact,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (showFull56List) "🔼 Thu gọn 6 Nhóm Mod" else "📋 Xem 6 Nhóm (Đã cài $downloaded56Count/56)",
                    color = Mint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showFull56List) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FieldGray)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "🗂️ Phân loại 56 Mod theo 6 Nhóm Chức Năng (Bấm chọn nhóm để lọc hoặc cài nguyên nhóm):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Indigo
                )

                // ── 6 Nhóm Phân Loại 56 Mod ──
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val allSelected = selectedCategoryGroup == "ALL"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (allSelected) Indigo else MaterialTheme.colorScheme.surface)
                            .clickable { selectedCategoryGroup = "ALL" }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            if (downloaded56Count >= all56Items.size) {
                                "✅ Tất cả 6 Nhóm (Đã cài $downloaded56Count/${all56Items.size})"
                            } else {
                                "📦 Tất cả 6 Nhóm ($downloaded56Count/${all56Items.size})"
                            },
                            color = if (allSelected) Color.White else Indigo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    categoryGroups.forEach { groupName ->
                        val (installedInGroup, groupCount) = groupCountsByTitle[groupName] ?: (0 to 0)
                        val isGroupFull = groupCount > 0 && installedInGroup >= groupCount
                        val isGroupSelected = selectedCategoryGroup == groupName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isGroupSelected -> Indigo
                                        isGroupFull -> MintSoft
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                                .clickable { selectedCategoryGroup = groupName }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                if (isGroupFull) {
                                    "✅ $groupName (Đã cài $installedInGroup/$groupCount)"
                                } else if (installedInGroup > 0) {
                                    "$groupName (Đã cài $installedInGroup/$groupCount)"
                                } else {
                                    "$groupName (0/$groupCount)"
                                },
                                color = when {
                                    isGroupSelected -> Color.White
                                    isGroupFull -> Mint
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontSize = 10.sp,
                                fontWeight = if (isGroupSelected || isGroupFull) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Nếu người dùng đang chọn 1 nhóm cụ thể -> Hiện trạng thái Đã cài hoặc nút Cài Nhanh Nguyên Nhóm đó
                if (selectedCategoryGroup != "ALL") {
                    val (installedInSelectedGroup, groupOnlyCount) = groupCountsByTitle[selectedCategoryGroup] ?: (0 to 0)
                    val missingInSelectedGroup = (groupOnlyCount - installedInSelectedGroup).coerceAtLeast(0)

                    if (installedInSelectedGroup >= groupOnlyCount) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintSoft)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "✅ Nhóm '$selectedCategoryGroup' đã tải & cài đặt đủ ($installedInSelectedGroup/$groupOnlyCount Mod)",
                                color = Mint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                vm.installUserScreenshotModCategory(
                                    categoryGroup = selectedCategoryGroup,
                                    includeCoreLibs = true,
                                    customMcVersion = selectedCustomVersion
                                )
                            },
                            enabled = activeCoreExists && !serverRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                            shape = McShape.ControlCompact,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (installedInSelectedGroup > 0) {
                                    "➕ Đã cài $installedInSelectedGroup/$groupOnlyCount · Tải bổ sung $missingInSelectedGroup Mod thiếu của '$selectedCategoryGroup'"
                                } else if (selectedCategoryGroup == "🧱 Thư viện Lõi") {
                                    "⚡ Tải & Cài trọn bộ $selectedCategoryGroup ($groupOnlyCount Mod • MC $effectiveMcVer)"
                                } else {
                                    "⚡ Tải & Cài Nhóm '$selectedCategoryGroup' ($groupOnlyCount Mod + Kèm Thư viện Lõi • MC $effectiveMcVer)"
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Ô tìm kiếm nhanh trong 56 Mod
                OutlinedTextField(
                    value = catalogSearchQuery,
                    onValueChange = { catalogSearchQuery = it },
                    placeholder = { Text("🔍 Tìm trong 56 Mod (VD: Macaw, Farmer, Lithium, Voice, Waystones...)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = McShape.ControlCompact
                )

                // Hiển thị danh sách Mod theo từng nhóm rõ ràng (LazyColumn mượt 60-120FPS)
                val groupedDisplay = remember(filteredCatalogItems, selectedCategoryGroup, categoryGroups) {
                    if (selectedCategoryGroup == "ALL") {
                        categoryGroups.mapNotNull { grp ->
                            val list = filteredCatalogItems.filter { it.categoryGroup == grp }
                            if (list.isEmpty()) null else grp to list
                        }
                    } else {
                        listOf(selectedCategoryGroup to filteredCatalogItems)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupedDisplay.forEach { (grpTitle, grpItems) ->
                        val grpInstalledCount = grpItems.count { matchedModBySlug[it.slug] != null }
                        val grpIsFull = grpItems.isNotEmpty() && grpInstalledCount >= grpItems.size
                        item(key = "grp_hdr_$grpTitle") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$grpTitle (Đã cài $grpInstalledCount/${grpItems.size} mod)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (grpIsFull) Mint else Indigo,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selectedCategoryGroup == "ALL") {
                                    if (grpIsFull) {
                                        Text(
                                            "✅ Đã cài đủ (${grpInstalledCount}/${grpItems.size})",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Mint,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MintSoft)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    } else {
                                        Text(
                                            if (grpInstalledCount > 0) "➕ Tải thiếu (${grpItems.size - grpInstalledCount} mod)" else "⚡ Cài nhóm này",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Mint,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable(enabled = activeCoreExists && !serverRunning) {
                                                    vm.installUserScreenshotModCategory(
                                                        categoryGroup = grpTitle,
                                                        includeCoreLibs = true,
                                                        customMcVersion = selectedCustomVersion
                                                    )
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        items(grpItems, key = { it.slug }) { item ->
                            val matchedMod = matchedModBySlug[item.slug]
                            val isInstalled = matchedMod != null
                            val isEnabledMod = matchedMod?.isEnabled == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AddonJarIcon(
                                    jarPath = matchedMod?.filePath,
                                    baseName = item.title,
                                    slugHint = item.slug,
                                    isEnabled = matchedMod?.isEnabled ?: true,
                                    size = 38.dp
                                )
                                Spacer(Modifier.size(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "#${item.index} · ${item.title}",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.descVi,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BadgeChip(item.weightVi, Mint, MintSoft)
                                        BadgeChip(item.scopeVi, Indigo, IndigoSoft)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (matchedMod != null) {
                                            "✅ Đã tải tệp: ${matchedMod.fileName} (${matchedMod.sizeText})"
                                        } else {
                                            "Chưa tải (${item.originalJarName})"
                                        },
                                        fontSize = 11.sp,
                                        color = if (matchedMod != null) Mint else Muted,
                                        fontWeight = if (matchedMod != null) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.size(8.dp))
                                if (isInstalled && matchedMod != null) {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isEnabledMod) MintSoft else CoralSoft)
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                if (isEnabledMod) "🟢 Đang bật" else "⏸ Đang tắt",
                                                color = if (isEnabledMod) Mint else Coral,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Switch(
                                            checked = isEnabledMod,
                                            onCheckedChange = {
                                                pendingCatalogModToggle = item to matchedMod
                                            },
                                            enabled = activeCoreExists && !serverRunning
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            vm.installSingleCatalogMod(item, customMcVersion = selectedCustomVersion)
                                        },
                                        enabled = activeCoreExists && !serverRunning,
                                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("➕ Cài", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingCatalogModToggle?.let { (catalogItem, modEntry) ->
        val willEnable = !modEntry.isEnabled
        AlertDialog(
            onDismissRequest = { pendingCatalogModToggle = null },
            title = {
                Text(
                    if (willEnable) "🟢 Xác nhận BẬT Mod '${catalogItem.title}'?"
                    else "⚠️ Cảnh báo trước khi TẮT Mod '${catalogItem.title}'?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Nhóm phân loại: ${catalogItem.categoryGroup} • Tệp: ${modEntry.fileName} (${modEntry.sizeText})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Indigo
                    )
                    Text(
                        if (willEnable) {
                            "Bạn sắp BẬT lại mod '${catalogItem.title}'.\n" +
                                "• Chức năng: ${catalogItem.descVi}\n" +
                                "• Phạm vi: ${catalogItem.scopeVi}\n\n" +
                                "Hãy đảm bảo Server đang dừng và đã bật các Thư viện Lõi cần thiết trước khi khởi động."
                        } else {
                            "Bạn sắp TẮT mod '${catalogItem.title}' (${catalogItem.descVi}).\n\n" +
                                (if (catalogItem.categoryGroup.contains("Thư viện Lõi")) {
                                    "🚨 CẢNH BÁO ĐẶC BIỆT: Đây là Mod thuộc nhóm '🧱 Thư viện Lõi'! Nếu bạn tắt mod này, các mod khác phụ thuộc vào nó sẽ bị lỗi không thể khởi động Server!"
                                } else if (catalogItem.scopeVi.contains("Server + Client")) {
                                    "⚠️ CẢNH BÁO DỮ LIỆU THẾ GIỚI: Đây là Mod thêm nội dung (${catalogItem.categoryGroup}). Nếu bạn tắt mod này rồi mở Map đã từng đặt các khối/vật phẩm của mod, những khối đó trong Map sẽ biến mất!"
                                } else {
                                    "ℹ️ Đây là Mod Tối ưu / Server-side (${catalogItem.categoryGroup}). Bạn có thể tắt/bật an toàn mà không làm mất khối trong Map, nhưng tốc độ TPS/RAM có thể giảm."
                                })
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetFile = modEntry.fileName
                        pendingCatalogModToggle = null
                        vm.toggleModEnabled(targetFile)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (willEnable) Mint else Coral)
                ) {
                    Text(
                        if (willEnable) "🟢 Đồng ý Bật Mod" else "⏸ Đồng ý Tắt Mod",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCatalogModToggle = null }) {
                    Text("Hủy", color = Muted)
                }
            }
        )
    }

    if (showServerTransferDialog) {
        AlertDialog(
            onDismissRequest = { showServerTransferDialog = false },
            title = { Text("🔄 Chuyển Mod & Config Từ Server Khác", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (otherServers.isEmpty()) {
                        Text(
                            "Hiện chỉ có 1 server trong ứng dụng. Nếu bộ mod cũ của bạn nằm trong thư mục điện thoại hoặc từ launcher khác (Pojav/Zalith/FCL), hãy bấm nút '📂 Tải Thư Mục Full Mod' hoặc '⚡ Dọn Mod Lạ & Tải ĐỦ 56/56 Mod Gốc' nhé!",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            "Chọn server cũ mà bạn muốn lấy toàn bộ Mod chuyển sang server「${config.activeCoreName ?: ""}」:",
                            fontSize = 12.sp,
                            color = Muted
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyConfigsToo, onCheckedChange = { copyConfigsToo = it })
                            Text("Sao chép kèm cả thư mục cấu hình (config/)", fontSize = 12.sp)
                        }
                        otherServers.forEach { srv ->
                            OutlinedButton(
                                onClick = {
                                    showServerTransferDialog = false
                                    vm.copyModsFromAnotherServer(srv.dirName, copyConfigsToo)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = McShape.ControlCompact
                            ) {
                                Text(
                                    "Chuyển từ: ${srv.name} (${srv.core.displayName} ${srv.version})",
                                    color = Indigo,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerTransferDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    if (showAutoPackConfirmDialog) {
        var cleanAllNon56 by remember { mutableStateOf(true) }
        var forceReinstallAll by remember { mutableStateOf(false) }
        val missingCountDialog = (all56Items.size - downloaded56Count).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { showAutoPackConfirmDialog = false },
            title = {
                Text(
                    if (downloaded56Count >= all56Items.size) {
                        "✅ Đã Cài Đủ 56/56 Mod (MC $effectiveMcVer)"
                    } else {
                        "⚡ Cài Đặt Bộ 56 Mod Gốc (MC $effectiveMcVer)"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (downloaded56Count >= all56Items.size) MintSoft else IndigoSoft)
                            .padding(8.dp)
                    ) {
                        Text(
                            if (downloaded56Count >= all56Items.size) {
                                "✅ Máy chủ đã tải & cài đặt đủ 56/56 Mod! Hệ thống sẽ không tải lại các mod đã có (trừ khi bạn tích chọn 'Tải lại từ đầu' bên dưới)."
                            } else {
                                "📊 Trạng thái hiện tại: Đã cài $downloaded56Count/56 Mod · Còn thiếu $missingCountDialog Mod.\nHệ thống sẽ giữ nguyên $downloaded56Count mod đã có và CHỈ TẢI BỔ SUNG $missingCountDialog mod còn thiếu!"
                            },
                            fontSize = 11.sp,
                            color = if (downloaded56Count >= all56Items.size) Mint else Indigo,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 15.sp
                        )
                    }
                    Text(
                        "• ⚡ Tối ưu & Chống Lag (14 Mod): Alternate Current, C2ME, Chunky, Debugify, EntityCulling, FerriteCore, Ksyxis, Lithium, ModernFix-mVUS, NoisiumForked, ServerCore, Sodium, Stfu, ThreadTweak.\n" +
                            "• 🧱 Thư viện Lõi (11 Mod): Architectury API, Balm, Cloth Config, Fabric API, Forge Config API Port, JamLib, MaLiLib, Player Animation Lib, Polymer, Puzzles Lib, YACL.\n" +
                            "• 🏠 Nội thất Macaw's & Xây dựng (14 Mod): Trọn bộ 12 mod Macaw's + Furnish + Litematica.\n" +
                            "• 🍳 Ẩm thực & Nông trại (8 Mod): Farmer's Delight Refabricated, 3D Placeable Food, Chef's Delight, Cooking for Blockheads, Crate Delight, More Delight, Rustic Delight, Ube's Delight.\n" +
                            "• 🌿 Tiện ích & Voice Chat (6 Mod): Simple Voice Chat, Waystones, AppleSkin, Emotecraft, Quick Leaf Decay, RightClickHarvest.\n" +
                            "• 🐾 Động vật & Sinh vật (3 Mod): Animal Garden (Manatee & Mouse), Tom's Mobs.",
                        fontSize = 11.sp,
                        color = Muted,
                        lineHeight = 16.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(FieldGray)
                            .clickable { cleanAllNon56 = !cleanAllNon56 }
                            .padding(8.dp)
                    ) {
                        Checkbox(checked = cleanAllNon56, onCheckedChange = { cleanAllNon56 = it })
                        Text(
                            "🧹 Dọn sạch các mod lạ không nằm trong 56 mod này (Khuyên dùng để sửa dứt điểm lỗi server)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (downloaded56Count > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(FieldGray)
                                .clickable { forceReinstallAll = !forceReinstallAll }
                                .padding(8.dp)
                        ) {
                            Checkbox(checked = forceReinstallAll, onCheckedChange = { forceReinstallAll = it })
                            Text(
                                "🔄 Tải lại từ đầu cả $downloaded56Count mod đã cài (Chỉ bật nếu file mod cũ bị hỏng)",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAutoPackConfirmDialog = false
                        vm.installUserScreenshotModpackFromModrinth(
                            cleanOtherMods = cleanAllNon56,
                            customMcVersion = selectedCustomVersion,
                            forceReinstallAll = forceReinstallAll
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Mint)
                ) {
                    Text(
                        if (!forceReinstallAll && missingCountDialog in 1 until all56Items.size) {
                            "Tải bổ sung $missingCountDialog Mod thiếu ($effectiveMcVer)"
                        } else if (!forceReinstallAll && downloaded56Count >= all56Items.size) {
                            "Kiểm tra & Dọn mod lạ"
                        } else {
                            "Tải & Cài Đủ 56 Mod ($effectiveMcVer)"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoPackConfirmDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun ResourcePackConfigCard(
    vm: McViewModel,
    activeCoreExists: Boolean
) {
    val props by vm.serverProperties.collectAsStateWithLifecycle()
    var url by remember(props["resource-pack"]) { mutableStateOf(props["resource-pack"].orEmpty()) }
    var sha1 by remember(props["resource-pack-sha1"]) { mutableStateOf(props["resource-pack-sha1"].orEmpty()) }
    var prompt by remember(props["resource-pack-prompt"]) { mutableStateOf(props["resource-pack-prompt"].orEmpty()) }
    var requirePack by remember(props["require-resource-pack"]) {
        mutableStateOf(props["require-resource-pack"]?.equals("true", ignoreCase = true) == true)
    }

    McCard(title = "🎨 Quản Lý Gói Tài Nguyên (Resource Pack)") {
        Text(
            "💡 Phân biệt rõ 2 cách dùng Gói Tài Nguyên (Texture Pack / Resource Pack):\n" +
                "1️⃣ Cài riêng trên máy người chơi: Người chơi tự chép tệp .zip vào thư mục `resourcepacks/` trong game của họ (không bắt buộc cài trên server).\n" +
                "2️⃣ Đồng bộ tự động từ Server: Nhập đường dẫn tải trực tiếp (.zip) bên dưới để mọi người chơi khi vào server đều được tự động tải gói tài nguyên về máy!",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Đường dẫn tải trực tiếp Resource Pack (.zip URL)", fontSize = 11.sp) },
            placeholder = { Text("https://example.com/resourcepack.zip", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = sha1,
            onValueChange = { sha1 = it },
            label = { Text("Mã kiểm tra SHA-1 (Tùy chọn, giúp game xác thực tệp)", fontSize = 11.sp) },
            placeholder = { Text("Để trống nếu chưa có mã SHA-1", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Lời nhắn hiển thị khi mời người chơi tải gói", fontSize = 11.sp) },
            placeholder = { Text("Hãy tải gói tài nguyên của server để hiển thị đầy đủ hình ảnh!", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(FieldGray)
                .clickable { requirePack = !requirePack }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Checkbox(checked = requirePack, onCheckedChange = { requirePack = it })
            Text(
                "Bắt buộc người chơi phải chấp nhận Gói tài nguyên mới được vào server (require-resource-pack)",
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { vm.applyServerResourcePack(url, sha1, prompt, requirePack) },
                enabled = activeCoreExists,
                colors = ButtonDefaults.buttonColors(containerColor = Mint),
                shape = McShape.ControlCompact,
                modifier = Modifier.weight(1f)
            ) {
                Text("💾 Lưu Gói Tài Nguyên Server", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (url.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        url = ""
                        sha1 = ""
                        prompt = ""
                        requirePack = false
                        vm.applyServerResourcePack("", "", "", false)
                    },
                    enabled = activeCoreExists,
                    shape = McShape.ControlCompact
                ) {
                    Text("Xóa", color = Coral, fontSize = 11.sp)
                }
            }
        }
    }
}
