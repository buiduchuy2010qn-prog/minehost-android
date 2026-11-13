package com.mineserve.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.BuildConfig
import com.mineserve.mobile.R
import com.mineserve.mobile.data.AptMirror
import com.mineserve.mobile.data.DownloadMirror
import com.mineserve.mobile.ui.DebouncedTextField
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.LocalHorizonChromePadding
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegPill
import com.mineserve.mobile.ui.SubPage
import com.mineserve.mobile.ui.theme.AppUiSettings
import com.mineserve.mobile.ui.theme.EyeCareColorPreset
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import com.mineserve.mobile.ui.theme.horizonGlassTokensFor
import kotlin.math.abs
import kotlin.math.roundToInt

private data class PresetSwatchMeta(
    val skyTopColor: Color,
    val skyHorizonColor: Color,
    val glowColor: Color,
    val subtitleVi: String
)

private fun swatchMetaFor(preset: EyeCareColorPreset): PresetSwatchMeta {
    val tokens = horizonGlassTokensFor(preset)
    val subtitle = when (preset) {
        EyeCareColorPreset.SoftSlate -> "Hoàng hôn vũ trụ dịu mắt"
        EyeCareColorPreset.BlockWayDark -> "Kính đêm xanh lam sắc nét"
        EyeCareColorPreset.WarmMocha -> "Đêm cam Rust ấm áp"
        EyeCareColorPreset.SakuraGlass -> "Sương mai anh đào trong trẻo"
        EyeCareColorPreset.WarmPaperLight -> "Băng pha lê sáng rõ ban ngày"
        EyeCareColorPreset.MojangEmerald -> "Lục bảo Mojang cổ điển"
        EyeCareColorPreset.ModrinthMint -> "Ngọc bích Modrinth tươi mát"
        EyeCareColorPreset.FeatherSky -> "Xanh băng Feather thanh thoát"
        EyeCareColorPreset.CherryDark -> "Đêm anh đào huyền ảo"
    }
    return PresetSwatchMeta(
        skyTopColor = tokens.skyTop,
        skyHorizonColor = tokens.horizonGlow,
        glowColor = tokens.accent,
        subtitleVi = subtitle
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: McViewModel, onNavigate: (SubPage) -> Unit = {}) {
    val config by vm.config.collectAsState()
    val context = LocalContext.current
    val lastCheckResult by vm.lastUpdateCheckResult.collectAsState()
    val glass = HorizonGlass
    val chromePad = LocalHorizonChromePadding.current

    LaunchedEffect(context) {
        AppUiSettings.ensureLoaded(context)
    }
    val activePreset by AppUiSettings.colorPreset.collectAsState()
    val activeGlassOpacity by AppUiSettings.glassOpacity.collectAsState()
    val activeFontScale by AppUiSettings.fontScale.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
    ) {
        HeaderBlock(
            eyebrow = stringResource(R.string.eyebrow_settings),
            title = stringResource(R.string.s186)
        )

        // ── Flagship: BlockWay Pure Horizon Glass Studio ──
        McCard(title = "🎨 Xưởng Giao Diện Pure Horizon Glass") {
            Text(
                "Chọn 1 trong 9 bảng màu bầu trời BlockWay Horizon, chỉnh độ trong suốt mặt kính trực tiếp và cỡ chữ tiếng Việt theo ý thích.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.5.sp
            )
            Spacer(Modifier.height(12.dp))

            // 2-column responsive visual swatch grid iterating all 9 EyeCareColorPreset.entries
            val rows = remember { EyeCareColorPreset.entries.chunked(2) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { preset ->
                            val selected = preset == activePreset
                            val meta = swatchMetaFor(preset)
                            PresetSwatchTile(
                                preset = preset,
                                meta = meta,
                                selected = selected,
                                onClick = { AppUiSettings.setColorPreset(context, preset) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Live Glass Transparency Studio Sub-Plate (14% – 52%)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(
                        shape = McShape.Row,
                        containerColor = glass.glassRow,
                        borderColor = glass.hairline
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "🔮 Độ trong suốt mặt kính (Pure Horizon Glass)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(glass.accentSoft)
                            .border(1.dp, glass.accent.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${(activeGlassOpacity * 100f).roundToInt()}%",
                            style = HorizonTabularStyle,
                            color = glass.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Kéo sang trái (14%–18%) để nhìn xuyên thấu bầu trời sao & ráng chiều rõ nhất; kéo sang phải (38%–52%) khi cần nền thẻ đậm hơn ngoài nắng.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.5.sp
                )

                Slider(
                    value = activeGlassOpacity,
                    onValueChange = { AppUiSettings.setGlassOpacity(context, it) },
                    valueRange = 0.14f..0.52f,
                    colors = SliderDefaults.colors(
                        thumbColor = glass.accent,
                        activeTrackColor = glass.accent,
                        inactiveTrackColor = glass.hairline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        0.16f to "✨ Pha lê Siêu Trong 16%",
                        0.26f to "🌌 Chuẩn Horizon 26%",
                        0.42f to "🛡️ Kính Mờ Đậm 42%"
                    ).forEach { (opacityVal, label) ->
                        val isCurrentOpacity = abs(activeGlassOpacity - opacityVal) < 0.02f
                        SegPill(
                            text = label,
                            selected = isCurrentOpacity,
                            compact = true,
                            onClick = { AppUiSettings.setGlassOpacity(context, opacityVal) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Font Scale Studio Sub-Plate (90% – 125%) + Live Vietnamese Sample Preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(
                        shape = McShape.Row,
                        containerColor = glass.glassRow,
                        borderColor = glass.hairline
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "🔤 Tỷ lệ cỡ chữ hiển thị",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(glass.accentSoft)
                            .border(1.dp, glass.accent.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${(activeFontScale * 100f).roundToInt()}%",
                            style = HorizonTabularStyle,
                            color = glass.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Slider(
                    value = activeFontScale,
                    onValueChange = { AppUiSettings.setFontScale(context, it) },
                    valueRange = 0.90f..1.25f,
                    colors = SliderDefaults.colors(
                        thumbColor = glass.accent,
                        activeTrackColor = glass.accent,
                        inactiveTrackColor = glass.hairline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        0.92f to "92% Gọn",
                        1.00f to "100%",
                        1.04f to "104% Chuẩn",
                        1.12f to "112% Rõ",
                        1.20f to "120% Lớn"
                    ).forEach { (scaleVal, label) ->
                        val isCurrentScale = abs(activeFontScale - scaleVal) < 0.015f
                        SegPill(
                            text = label,
                            selected = isCurrentScale,
                            compact = true,
                            onClick = { AppUiSettings.setFontScale(context, scaleVal) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Live Vietnamese typography & tabular preview plate
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(glass.glassInput)
                        .border(1.dp, glass.hairline, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Xem trước hiển thị tiếng Việt",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = glass.accent
                        )
                        Text(
                            "🟢 20.0 TPS · 1,024 MB",
                            style = HorizonTabularStyle,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = glass.liveGreen
                        )
                    }
                    Text(
                        "Trải nghiệm quản trị máy chủ Minecraft trên điện thoại sắc nét, thoáng mắt, không dính dấu tiếng Việt (ệ, ử, ẫ, ộ).",
                        fontSize = 12.sp,
                        lineHeight = 17.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

            // JVM 内存上限
            McCard(title = stringResource(R.string.s1003)) {
                Text(
                    stringResource(R.string.s1004),
                    color = Muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.s1005),
                    color = glass.dangerCoral,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2048, 3072, 4096, 6144, 8192).forEach { presetMb ->
                        SegPill(
                            text = "${presetMb / 1024} GB ($presetMb)",
                            selected = config.maxHeapMb == presetMb,
                            compact = true,
                            onClick = { vm.setMaxHeap(presetMb) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                DebouncedTextField(
                    value = config.maxHeapMb.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { raw ->
                            val mb = raw.coerceIn(256, 65536)
                            if (mb != raw) {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.set_max_heap_range_hint),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            vm.setMaxHeap(mb)
                        }
                    },
                    sanitize = { it.filter(Char::isDigit) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.s1006)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 下载源设置（Termux bootstrap rootfs 下载源）
            McCard(title = stringResource(R.string.s1007)) {
                Text(
                    stringResource(R.string.st_mirror_hint),
                    color = Muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.5.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.s1009), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DownloadMirror.values().forEach { mirror ->
                        SegPill(
                            text = mirror.displayName,
                            selected = config.downloadMirror == mirror,
                            onClick = { vm.setDownloadMirror(mirror) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val testing by vm.mirrorTestRunning.collectAsState()
                val testResult = vm.mirrorTestResult.collectAsState().value
                Button(
                    onClick = { vm.testDownloadSources() },
                    enabled = !testing,
                    colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                    shape = McShape.Control,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(if (testing) R.string.st_mirror_test_running else R.string.st_mirror_test),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (testResult != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(testResult, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }

            // APT 镜像设置（JDK/wget/frp 等依赖包下载源）
            McCard(title = stringResource(R.string.s1010)) {
                Text(
                    stringResource(R.string.s1011),
                    color = Muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.5.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.s1012), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AptMirror.values().forEach { mirror ->
                        SegPill(
                            text = mirror.displayName,
                            selected = config.aptMirror == mirror,
                            onClick = { vm.setAptMirror(mirror) }
                        )
                    }
                }
            }

            // 多线程下载设置（内置下载加速模块）
            McCard(title = stringResource(R.string.s1064)) {
                val enabledState = remember { mutableStateOf(vm.isMultiThreadDownloadEnabled()) }
                val threadState = remember { mutableStateOf(vm.downloadThreadCount()) }
                SettingToggle(
                    title = stringResource(R.string.s1065),
                    subtitle = stringResource(R.string.s1066),
                    checked = enabledState.value,
                    onChange = {
                        enabledState.value = it
                        vm.setMultiThreadDownloadEnabled(it)
                    }
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.s1067), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2, 4, 8).forEach { count ->
                        SegPill(
                            text = stringResource(R.string.st_threads_count, count),
                            selected = threadState.value == count && enabledState.value,
                            onClick = {
                                threadState.value = count
                                vm.setDownloadThreadCount(count)
                            }
                        )
                    }
                }
            }

            // 保活与恢复
            McCard(title = stringResource(R.string.s1013)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingToggle(
                        title = stringResource(R.string.s396),
                        subtitle = stringResource(R.string.s397),
                        checked = config.autoRestartOnCrash,
                        onChange = { vm.setAutoRestart(it) }
                    )
                    SettingToggle(
                        title = stringResource(R.string.s1014),
                        subtitle = stringResource(R.string.s1015),
                        checked = config.keepWifiLock,
                        onChange = { vm.setKeepWifiLock(it) }
                    )
                    SettingToggle(
                        title = stringResource(R.string.s1016),
                        subtitle = stringResource(R.string.s1017),
                        checked = config.keepCpuWakelock,
                        onChange = { vm.setKeepCpuWakelock(it) }
                    )
                }
            }

            // 服务器事件通知
            McCard(title = stringResource(R.string.set_notify_title)) {
                Text(
                    stringResource(R.string.set_notify_hint),
                    color = Muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.5.sp
                )
                Spacer(Modifier.height(8.dp))
                val notifyLevel by vm.eventNotifyLevel.collectAsState()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SegPill(
                        text = stringResource(R.string.set_notify_off),
                        selected = notifyLevel == 0,
                        onClick = { vm.setEventNotifyLevel(0) }
                    )
                    SegPill(
                        text = stringResource(R.string.set_notify_important),
                        selected = notifyLevel == 1,
                        onClick = { vm.setEventNotifyLevel(1) }
                    )
                    SegPill(
                        text = stringResource(R.string.set_notify_all),
                        selected = notifyLevel == 2,
                        onClick = { vm.setEventNotifyLevel(2) }
                    )
                }
            }

            // 后台保活（大尺寸独立入口）
            McCard(title = stringResource(R.string.s542)) {
                Text(
                    stringResource(R.string.s1018),
                    color = Muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.5.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onNavigate(SubPage.KeepAlive) },
                    colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                    shape = McShape.Control,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        stringResource(R.string.s1019),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 快捷入口（子页面跳转）
            McCard(title = stringResource(R.string.s1020)) {
                SettingEntry(
                    title = stringResource(R.string.s1021),
                    subtitle = stringResource(R.string.s1022),
                    onClick = { onNavigate(SubPage.DownloadHelp) }
                )
            }

            // 关于
            McCard(title = stringResource(R.string.s1023)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingEntry(
                        title = stringResource(R.string.update_current_version),
                        subtitle = "v${BuildConfig.VERSION_NAME}",
                        tabularSubtitle = true,
                        showChevron = false,
                        onClick = null
                    )
                    SettingEntry(
                        title = stringResource(R.string.update_check),
                        subtitle = lastCheckResult ?: stringResource(R.string.s1025),
                        onClick = { vm.checkForUpdate(manual = true) }
                    )
                }
            }

            // 意见反馈
            McCard(title = stringResource(R.string.s1026)) {
                Text(
                    stringResource(R.string.s1027),
                    color = Muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.5.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/buiduchuy2010qn-prog/minehost-android/issues")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Thiết bị không có trình duyệt
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                    shape = McShape.ControlCompact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.s1029),
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(chromePad.calculateBottomPadding() + 28.dp))
    }
}

@Composable
private fun PresetSwatchTile(
    preset: EyeCareColorPreset,
    meta: PresetSwatchMeta,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = HorizonGlass
    Column(
        modifier = modifier
            .horizonGlassPlate(
                shape = RoundedCornerShape(14.dp),
                containerColor = if (selected) glass.accentSoft else glass.glassRow,
                borderColor = if (selected) glass.accent else glass.hairline,
                highlighted = selected
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Dual-color diagonal BlockWay swatch preview
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) glass.accent else glass.hairline,
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(meta.skyTopColor, meta.skyHorizonColor)
                        )
                    )
                    val lowerTriangle = Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path = lowerTriangle, color = meta.glowColor)
                }
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(glass.liveGreenSoft)
                        .border(1.dp, glass.liveGreen.copy(alpha = 0.45f), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        "✓ Đang dùng",
                        color = glass.liveGreen,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            preset.labelVi,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 17.sp,
            color = if (selected) glass.accent else MaterialTheme.colorScheme.onSurface
        )
        Text(
            meta.subtitleVi,
            fontSize = 10.5.sp,
            lineHeight = 14.5.sp,
            color = Muted
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val glass = HorizonGlass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = glass.hairline
            )
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 15.5.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingEntry(
    title: String,
    subtitle: String,
    tabularSubtitle: Boolean = false,
    showChevron: Boolean = true,
    onClick: (() -> Unit)?
) {
    val glass = HorizonGlass
    val baseMod = Modifier
        .fillMaxWidth()
        .horizonGlassPlate(
            shape = McShape.Row,
            containerColor = glass.glassRow,
            borderColor = glass.hairline
        )
    val interactiveMod = if (onClick != null) baseMod.clickable(onClick = onClick) else baseMod
    Row(
        modifier = interactiveMod.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = glass.accent,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = if (tabularSubtitle) HorizonTabularStyle else MaterialTheme.typography.bodySmall,
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 15.5.sp
            )
        }
        if (showChevron) {
            Text("→", color = glass.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
