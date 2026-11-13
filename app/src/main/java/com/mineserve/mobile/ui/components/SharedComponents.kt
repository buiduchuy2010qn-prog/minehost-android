package com.mineserve.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.R
import com.mineserve.mobile.data.JavaRuntime
import com.mineserve.mobile.data.JavaVendor
import com.mineserve.mobile.data.JavaVersion
import com.mineserve.mobile.data.ProotDistro
import com.mineserve.mobile.data.ServerState
import com.mineserve.mobile.data.StepStatus
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Tiêu đề đầu trang chuẩn Pure Horizon Glass:
 * Tự động gọn lại khi trang đã nằm bên dưới thanh kính MineHostNavigation.
 */
@Composable
fun HeaderBlock(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    statusBarPadding: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val glass = HorizonGlass
    if (LocalMineHostChrome.current) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = McSpacing.Page, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing.invoke()
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(glass.glassChrome)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (statusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = McSpacing.Page, vertical = 10.dp)
        ) {
            Text(
                text = eyebrow,
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MinecraftPixelArtIcon(
                    kind = remember(title) { inferCardArtKind(title) },
                    size = 24.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(glass.hairline)
        )
    }
}

/**
 * Thanh quay lại trang con:
 * Khi nằm trong khung MineHostNavigation (`LocalMineHostChrome.current == true`),
 * tự động bỏ `.statusBarsPadding()` để triệt tiêu lỗi nhân đôi khoảng trống đỉnh màn hình.
 */
@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    val hasChrome = LocalMineHostChrome.current
    val glass = HorizonGlass
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (hasChrome) Color.Transparent else glass.glassChrome)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasChrome) Modifier else Modifier.statusBarsPadding())
                .padding(
                    start = if (hasChrome) McSpacing.Page else 8.dp,
                    end = McSpacing.Page,
                    top = 6.dp,
                    bottom = 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(McIcon.Touch)
                    .horizonGlassPlate(
                        shape = McShape.Pill,
                        containerColor = glass.glassRow,
                        borderColor = glass.hairline
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.s404),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(McIcon.Item)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            )
        }
        if (!hasChrome) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(glass.hairline)
            )
        }
    }
}

/** Trạng thái danh sách trống với quả cầu kính mờ Pure Horizon Glass. */
@Composable
fun EmptyHint(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val glass = HorizonGlass
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .horizonGlassPlate(
                    shape = McShape.Pill,
                    containerColor = glass.glassRow,
                    borderColor = glass.hairline
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = glass.accent,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            color = Muted,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Thẻ Hero tổng quan (giữ nguyên toàn bộ tham số cho các màn hình phụ).
 */
@Composable
fun HeroBlock(
    state: ServerState,
    coreLabel: String,
    cpuPercent: Int? = null,
    maxHeapMb: Int? = null,
    onlineModeEnabled: Boolean = false,
    whitelistEnabled: Boolean = false,
    isRefreshing: Boolean = false,
    lastRefreshAtMs: Long = 0L,
    skipStartupProgress: Boolean = false,
    onRefresh: (() -> Unit)? = null
) {
    val glass = HorizonGlass
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = McSpacing.Page, vertical = 8.dp)
            .horizonGlassPlate(
                shape = McShape.Card,
                containerColor = glass.glassCardStrong,
                borderColor = glass.hairline,
                highlighted = state.isRunning
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            val isStarting = state.isRunning && state.runningSinceMs == 0L && !skipStartupProgress
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MinecraftPixelArtIcon(
                        kind = if (state.isRunning) MinecraftArtKind.CREEPER else MinecraftArtKind.GRASS,
                        size = 24.dp
                    )
                    Spacer(Modifier.size(8.dp))
                    val dotColor = when {
                        isStarting -> glass.warnAmber
                        state.isRunning -> glass.liveGreen
                        else -> textSecondary.copy(alpha = 0.55f)
                    }
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        when {
                            isStarting -> stringResource(R.string.hero_starting, coreLabel)
                            state.isRunning -> stringResource(R.string.hero_running, coreLabel)
                            else -> stringResource(R.string.hero_stopped)
                        },
                        color = textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onRefresh != null) {
                    if (isRefreshing) {
                        Box(
                            modifier = Modifier.size(McIcon.TouchCompact),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(McIcon.Action),
                                strokeWidth = 2.dp,
                                color = glass.accent
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(McIcon.TouchCompact)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.hero_refresh),
                                tint = textSecondary,
                                modifier = Modifier.size(McIcon.Action)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroStat(
                    if (state.isRunning) String.format(java.util.Locale.US, "%.1f", state.tps) else "--",
                    stringResource(R.string.hero_tps),
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    if (state.isRunning) "${state.onlinePlayers}/${state.maxPlayers}" else "--/--",
                    stringResource(R.string.hero_online),
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    if (state.isRunning && state.runningSinceMs > 0)
                        formatUptime(android.os.SystemClock.elapsedRealtime() - state.runningSinceMs)
                    else "--",
                    stringResource(R.string.hero_uptime),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            CpuMeterBar(percent = cpuPercent)
            Spacer(Modifier.height(8.dp))
            MemoryBar(
                usedMb = if (state.isRunning) state.usedMemoryMb else 0L,
                maxMb = maxHeapMb?.toLong() ?: 0L,
                running = state.isRunning
            )
            if (!isRefreshing && lastRefreshAtMs > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Đã cập nhật · ${formatRefreshAgo(lastRefreshAtMs)}",
                    color = textSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (onlineModeEnabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.hero_online_mode_warning),
                    color = glass.dangerCoral,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (whitelistEnabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.hero_whitelist_on),
                    color = glass.warnAmber,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (isStarting) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.startupPhase.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(McShape.Pill),
                    color = glass.warnAmber,
                    trackColor = glass.glassInput
                )
                Spacer(Modifier.height(5.dp))
                val viPhaseLabel = when (state.startupPhase.label) {
                    "准备环境" -> "Chuẩn bị môi trường"
                    "启动 Java" -> "Khởi chạy Java"
                    "下载依赖" -> "Tải thư viện phụ thuộc"
                    "加载核心" -> "Nạp Core máy chủ"
                    "启动网络" -> "Khởi động mạng & cổng"
                    "创建世界" -> "Đang tải / tạo thế giới"
                    "启动完成" -> "Hoàn tất khởi động"
                    else -> state.startupPhase.label
                }
                Text(
                    "Giai đoạn khởi động: $viPhaseLabel (${(state.startupPhase.progress * 100).toInt()}%)",
                    color = textPrimary,
                    style = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle)
                )
                Text(
                    "Chuẩn bị môi trường · Tải thư viện · Khởi chạy Java · Nạp Core · Tạo thế giới · Mở mạng",
                    color = textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MemoryBar(usedMb: Long, maxMb: Long, running: Boolean) {
    val label = stringResource(R.string.hero_memory)
    val safeMax = maxMb.coerceAtLeast(0L)
    val safeUsed = usedMb.coerceIn(0L, if (safeMax > 0L) safeMax else usedMb)
    val ratio = if (safeMax > 0L) (safeUsed.toFloat() / safeMax.toFloat()).coerceIn(0f, 1f) else 0f
    val valueText = if (running) {
        "${formatMemoryFull(safeUsed)} / ${formatMemoryFull(safeMax)}"
    } else {
        "-- / ${formatMemoryFull(safeMax)}"
    }
    HeroMeterBar(label = label, ratio = ratio, valueText = valueText)
}

@Composable
private fun CpuMeterBar(percent: Int?) {
    val label = stringResource(R.string.hero_cpu)
    val value = percent?.coerceIn(0, 100)
    val ratio = (value ?: 0) / 100f
    HeroMeterBar(label = label, ratio = ratio, valueText = value?.let { "$it%" } ?: "--")
}

@Composable
private fun HeroMeterBar(label: String, ratio: Float, valueText: String) {
    val glass = HorizonGlass
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label, $valueText" }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                valueText,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(2f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(McShape.Pill)
                .background(glass.glassInput)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(McShape.Pill)
                    .background(Brush.horizontalGradient(listOf(glass.liveGreen, glass.skyCyan)))
            )
        }
    }
}

private fun formatMemoryFull(mb: Long): String =
    if (mb >= 1024) String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0) else "${mb} MB"

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "$value, $label"
        }
    ) {
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge.merge(HorizonTabularStyle)
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun formatUptime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L)) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h >= 100 -> "${h}h"
        h > 0 -> "${h}h${m}m"
        m > 0 -> stringResource(R.string.sc_minutes, m)
        else -> stringResource(R.string.sc_seconds, totalSec)
    }
}

private fun formatRefreshAgo(refreshAtMs: Long): String {
    val elapsedSec = ((System.currentTimeMillis() - refreshAtMs).coerceAtLeast(0L)) / 1000
    return when {
        elapsedSec < 5 -> "vừa xong"
        elapsedSec < 60 -> "${elapsedSec} giây trước"
        elapsedSec < 3600 -> "${elapsedSec / 60} phút trước"
        else -> "${elapsedSec / 3600} giờ trước"
    }
}

enum class MinecraftArtKind(val titleVi: String, val subtitleVi: String) {
    GRASS("Khối Cỏ (Grass Block)", "Sinh tồn cổ điển"),
    CREEPER("Creeper Xanh", "Biểu tượng Minecraft"),
    COMMAND_BLOCK("Khối Lệnh (Command Block)", "Máy chủ Kỹ thuật & Lệnh"),
    DIAMOND_SWORD("Kiếm Kim Cương", "PvP & Chiến đấu"),
    CHEST("Rương Kho Báu", "Lưu trữ & Gói Mod"),
    ENDER_EYE("Mắt Ender", "Khám phá & Dịch chuyển"),
    GOLDEN_APPLE("Táo Vàng Phù Phép", "Hardcore & Thử thách"),
    REDSTONE("Đá Đỏ (Redstone)", "Tối ưu & Tự động hóa")
}

private fun pixelGridFor(kind: MinecraftArtKind): Array<IntArray> = when (kind) {
    MinecraftArtKind.GRASS -> arrayOf(
        intArrayOf(0xFF22C55E.toInt(), 0xFF16A34A.toInt(), 0xFF22C55E.toInt(), 0xFF4ADE80.toInt(), 0xFF22C55E.toInt(), 0xFF16A34A.toInt(), 0xFF22C55E.toInt(), 0xFF15803D.toInt()),
        intArrayOf(0xFF16A34A.toInt(), 0xFF4ADE80.toInt(), 0xFF16A34A.toInt(), 0xFF22C55E.toInt(), 0xFF15803D.toInt(), 0xFF22C55E.toInt(), 0xFF4ADE80.toInt(), 0xFF16A34A.toInt()),
        intArrayOf(0xFF15803D.toInt(), 0xFF22C55E.toInt(), 0xFF854D0E.toInt(), 0xFF16A34A.toInt(), 0xFF22C55E.toInt(), 0xFF854D0E.toInt(), 0xFF16A34A.toInt(), 0xFF713F12.toInt()),
        intArrayOf(0xFF854D0E.toInt(), 0xFF713F12.toInt(), 0xFFA16207.toInt(), 0xFF854D0E.toInt(), 0xFF713F12.toInt(), 0xFFA16207.toInt(), 0xFF854D0E.toInt(), 0xFF713F12.toInt()),
        intArrayOf(0xFF713F12.toInt(), 0xFFA16207.toInt(), 0xFF854D0E.toInt(), 0xFF5C3409.toInt(), 0xFF854D0E.toInt(), 0xFF713F12.toInt(), 0xFFA16207.toInt(), 0xFF5C3409.toInt()),
        intArrayOf(0xFF854D0E.toInt(), 0xFF5C3409.toInt(), 0xFF713F12.toInt(), 0xFFA16207.toInt(), 0xFF713F12.toInt(), 0xFF854D0E.toInt(), 0xFF5C3409.toInt(), 0xFF854D0E.toInt()),
        intArrayOf(0xFF5C3409.toInt(), 0xFF854D0E.toInt(), 0xFFA16207.toInt(), 0xFF713F12.toInt(), 0xFF5C3409.toInt(), 0xFFA16207.toInt(), 0xFF713F12.toInt(), 0xFF5C3409.toInt()),
        intArrayOf(0xFF452606.toInt(), 0xFF5C3409.toInt(), 0xFF713F12.toInt(), 0xFF452606.toInt(), 0xFF713F12.toInt(), 0xFF5C3409.toInt(), 0xFF452606.toInt(), 0xFF5C3409.toInt())
    )
    MinecraftArtKind.CREEPER -> arrayOf(
        intArrayOf(0xFF22C55E.toInt(), 0xFF16A34A.toInt(), 0xFF4ADE80.toInt(), 0xFF22C55E.toInt(), 0xFF16A34A.toInt(), 0xFF22C55E.toInt(), 0xFF4ADE80.toInt(), 0xFF15803D.toInt()),
        intArrayOf(0xFF16A34A.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF22C55E.toInt(), 0xFF4ADE80.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF22C55E.toInt()),
        intArrayOf(0xFF4ADE80.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF16A34A.toInt(), 0xFF15803D.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF16A34A.toInt()),
        intArrayOf(0xFF22C55E.toInt(), 0xFF16A34A.toInt(), 0xFF22C55E.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF22C55E.toInt(), 0xFF4ADE80.toInt(), 0xFF15803D.toInt()),
        intArrayOf(0xFF15803D.toInt(), 0xFF4ADE80.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF16A34A.toInt(), 0xFF22C55E.toInt()),
        intArrayOf(0xFF22C55E.toInt(), 0xFF16A34A.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF22C55E.toInt(), 0xFF16A34A.toInt()),
        intArrayOf(0xFF16A34A.toInt(), 0xFF22C55E.toInt(), 0xFF111827.toInt(), 0xFF22C55E.toInt(), 0xFF16A34A.toInt(), 0xFF111827.toInt(), 0xFF15803D.toInt(), 0xFF4ADE80.toInt()),
        intArrayOf(0xFF15803D.toInt(), 0xFF16A34A.toInt(), 0xFF22C55E.toInt(), 0xFF15803D.toInt(), 0xFF22C55E.toInt(), 0xFF16A34A.toInt(), 0xFF22C55E.toInt(), 0xFF15803D.toInt())
    )
    MinecraftArtKind.COMMAND_BLOCK -> arrayOf(
        intArrayOf(0xFF9A5B3E.toInt(), 0xFFC27853.toInt(), 0xFFD98A63.toInt(), 0xFF9A5B3E.toInt(), 0xFF9A5B3E.toInt(), 0xFFD98A63.toInt(), 0xFFC27853.toInt(), 0xFF9A5B3E.toInt()),
        intArrayOf(0xFFC27853.toInt(), 0xFF7C472F.toInt(), 0xFFC27853.toInt(), 0xFF475569.toInt(), 0xFF475569.toInt(), 0xFFC27853.toInt(), 0xFF7C472F.toInt(), 0xFFC27853.toInt()),
        intArrayOf(0xFFD98A63.toInt(), 0xFFC27853.toInt(), 0xFF334155.toInt(), 0xFF38BDF8.toInt(), 0xFFFACC15.toInt(), 0xFF334155.toInt(), 0xFFC27853.toInt(), 0xFFD98A63.toInt()),
        intArrayOf(0xFF9A5B3E.toInt(), 0xFF475569.toInt(), 0xFFFACC15.toInt(), 0xFF22C55E.toInt(), 0xFFEF4444.toInt(), 0xFF38BDF8.toInt(), 0xFF475569.toInt(), 0xFF9A5B3E.toInt()),
        intArrayOf(0xFF9A5B3E.toInt(), 0xFF475569.toInt(), 0xFF38BDF8.toInt(), 0xFFEF4444.toInt(), 0xFF22C55E.toInt(), 0xFFFACC15.toInt(), 0xFF475569.toInt(), 0xFF9A5B3E.toInt()),
        intArrayOf(0xFFD98A63.toInt(), 0xFFC27853.toInt(), 0xFF334155.toInt(), 0xFFFACC15.toInt(), 0xFF38BDF8.toInt(), 0xFF334155.toInt(), 0xFFC27853.toInt(), 0xFFD98A63.toInt()),
        intArrayOf(0xFFC27853.toInt(), 0xFF7C472F.toInt(), 0xFFC27853.toInt(), 0xFF475569.toInt(), 0xFF475569.toInt(), 0xFFC27853.toInt(), 0xFF7C472F.toInt(), 0xFFC27853.toInt()),
        intArrayOf(0xFF9A5B3E.toInt(), 0xFFC27853.toInt(), 0xFFD98A63.toInt(), 0xFF9A5B3E.toInt(), 0xFF9A5B3E.toInt(), 0xFFD98A63.toInt(), 0xFFC27853.toInt(), 0xFF9A5B3E.toInt())
    )
    MinecraftArtKind.DIAMOND_SWORD -> arrayOf(
        intArrayOf(0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF06B6D4.toInt(), 0xFF67E8F9.toInt(), 0xFF22D3EE.toInt()),
        intArrayOf(0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF06B6D4.toInt(), 0xFF67E8F9.toInt(), 0xFF22D3EE.toInt(), 0xFF06B6D4.toInt()),
        intArrayOf(0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF06B6D4.toInt(), 0xFF67E8F9.toInt(), 0xFF22D3EE.toInt(), 0xFF06B6D4.toInt(), 0xFF0F172A.toInt()),
        intArrayOf(0xFF0F172A.toInt(), 0xFF0891B2.toInt(), 0xFF06B6D4.toInt(), 0xFF67E8F9.toInt(), 0xFF22D3EE.toInt(), 0xFF06B6D4.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt()),
        intArrayOf(0xFF0F172A.toInt(), 0xFF06B6D4.toInt(), 0xFF22D3EE.toInt(), 0xFF0891B2.toInt(), 0xFF06B6D4.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt()),
        intArrayOf(0xFF0F172A.toInt(), 0xFF854D0E.toInt(), 0xFF0891B2.toInt(), 0xFF06B6D4.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt()),
        intArrayOf(0xFF713F12.toInt(), 0xFFA16207.toInt(), 0xFF854D0E.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt()),
        intArrayOf(0xFF0891B2.toInt(), 0xFF713F12.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF0F172A.toInt())
    )
    MinecraftArtKind.CHEST -> arrayOf(
        intArrayOf(0xFF451A03.toInt(), 0xFF78350F.toInt(), 0xFF92400E.toInt(), 0xFFB45309.toInt(), 0xFFB45309.toInt(), 0xFF92400E.toInt(), 0xFF78350F.toInt(), 0xFF451A03.toInt()),
        intArrayOf(0xFF78350F.toInt(), 0xFFB45309.toInt(), 0xFFD97706.toInt(), 0xFFD97706.toInt(), 0xFFD97706.toInt(), 0xFFD97706.toInt(), 0xFFB45309.toInt(), 0xFF78350F.toInt()),
        intArrayOf(0xFF78350F.toInt(), 0xFF92400E.toInt(), 0xFFB45309.toInt(), 0xFFFACC15.toInt(), 0xFFFACC15.toInt(), 0xFFB45309.toInt(), 0xFF92400E.toInt(), 0xFF78350F.toInt()),
        intArrayOf(0xFF1F2937.toInt(), 0xFF1F2937.toInt(), 0xFF1F2937.toInt(), 0xFFFDE047.toInt(), 0xFFFDE047.toInt(), 0xFF1F2937.toInt(), 0xFF1F2937.toInt(), 0xFF1F2937.toInt()),
        intArrayOf(0xFF78350F.toInt(), 0xFFB45309.toInt(), 0xFFD97706.toInt(), 0xFFEAB308.toInt(), 0xFFEAB308.toInt(), 0xFFD97706.toInt(), 0xFFB45309.toInt(), 0xFF78350F.toInt()),
        intArrayOf(0xFF78350F.toInt(), 0xFF92400E.toInt(), 0xFFB45309.toInt(), 0xFFD97706.toInt(), 0xFFD97706.toInt(), 0xFFB45309.toInt(), 0xFF92400E.toInt(), 0xFF78350F.toInt()),
        intArrayOf(0xFF78350F.toInt(), 0xFF92400E.toInt(), 0xFFB45309.toInt(), 0xFFB45309.toInt(), 0xFFB45309.toInt(), 0xFFB45309.toInt(), 0xFF92400E.toInt(), 0xFF78350F.toInt()),
        intArrayOf(0xFF451A03.toInt(), 0xFF451A03.toInt(), 0xFF78350F.toInt(), 0xFF78350F.toInt(), 0xFF78350F.toInt(), 0xFF78350F.toInt(), 0xFF451A03.toInt(), 0xFF451A03.toInt())
    )
    MinecraftArtKind.ENDER_EYE -> arrayOf(
        intArrayOf(0xFF0F172A.toInt(), 0xFF064E3B.toInt(), 0xFF047857.toInt(), 0xFF10B981.toInt(), 0xFF10B981.toInt(), 0xFF047857.toInt(), 0xFF064E3B.toInt(), 0xFF0F172A.toInt()),
        intArrayOf(0xFF064E3B.toInt(), 0xFF047857.toInt(), 0xFF10B981.toInt(), 0xFF34D399.toInt(), 0xFF34D399.toInt(), 0xFF10B981.toInt(), 0xFF047857.toInt(), 0xFF064E3B.toInt()),
        intArrayOf(0xFF047857.toInt(), 0xFF10B981.toInt(), 0xFF6EE7B7.toInt(), 0xFF064E3B.toInt(), 0xFF064E3B.toInt(), 0xFF6EE7B7.toInt(), 0xFF10B981.toInt(), 0xFF047857.toInt()),
        intArrayOf(0xFF10B981.toInt(), 0xFF34D399.toInt(), 0xFF84CC16.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF84CC16.toInt(), 0xFF34D399.toInt(), 0xFF10B981.toInt()),
        intArrayOf(0xFF10B981.toInt(), 0xFF34D399.toInt(), 0xFF84CC16.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt(), 0xFF84CC16.toInt(), 0xFF34D399.toInt(), 0xFF10B981.toInt()),
        intArrayOf(0xFF047857.toInt(), 0xFF10B981.toInt(), 0xFF6EE7B7.toInt(), 0xFF064E3B.toInt(), 0xFF064E3B.toInt(), 0xFF6EE7B7.toInt(), 0xFF10B981.toInt(), 0xFF047857.toInt()),
        intArrayOf(0xFF064E3B.toInt(), 0xFF047857.toInt(), 0xFF10B981.toInt(), 0xFF34D399.toInt(), 0xFF34D399.toInt(), 0xFF10B981.toInt(), 0xFF047857.toInt(), 0xFF064E3B.toInt()),
        intArrayOf(0xFF0F172A.toInt(), 0xFF064E3B.toInt(), 0xFF047857.toInt(), 0xFF10B981.toInt(), 0xFF10B981.toInt(), 0xFF047857.toInt(), 0xFF064E3B.toInt(), 0xFF0F172A.toInt())
    )
    MinecraftArtKind.GOLDEN_APPLE -> arrayOf(
        intArrayOf(0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt(), 0xFF15803D.toInt(), 0xFF22C55E.toInt(), 0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt()),
        intArrayOf(0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt(), 0xFFD97706.toInt(), 0xFFFACC15.toInt(), 0xFFFACC15.toInt(), 0xFFD97706.toInt(), 0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt()),
        intArrayOf(0xFF1E1B4B.toInt(), 0xFFD97706.toInt(), 0xFFFDE047.toInt(), 0xFFFEF08A.toInt(), 0xFFFACC15.toInt(), 0xFFEAB308.toInt(), 0xFFD97706.toInt(), 0xFF1E1B4B.toInt()),
        intArrayOf(0xFF1E1B4B.toInt(), 0xFFD97706.toInt(), 0xFFFEF08A.toInt(), 0xFFFDE047.toInt(), 0xFFFACC15.toInt(), 0xFFEAB308.toInt(), 0xFFB45309.toInt(), 0xFF1E1B4B.toInt()),
        intArrayOf(0xFF1E1B4B.toInt(), 0xFFD97706.toInt(), 0xFFFACC15.toInt(), 0xFFFACC15.toInt(), 0xFFEAB308.toInt(), 0xFFD97706.toInt(), 0xFFB45309.toInt(), 0xFF1E1B4B.toInt()),
        intArrayOf(0xFF1E1B4B.toInt(), 0xFFB45309.toInt(), 0xFFEAB308.toInt(), 0xFFFACC15.toInt(), 0xFFEAB308.toInt(), 0xFFD97706.toInt(), 0xFF92400E.toInt(), 0xFF1E1B4B.toInt()),
        intArrayOf(0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt(), 0xFFB45309.toInt(), 0xFFD97706.toInt(), 0xFFD97706.toInt(), 0xFFB45309.toInt(), 0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt()),
        intArrayOf(0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt(), 0xFF92400E.toInt(), 0xFF92400E.toInt(), 0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt(), 0xFF1E1B4B.toInt())
    )
    MinecraftArtKind.REDSTONE -> arrayOf(
        intArrayOf(0xFF1F1215.toInt(), 0xFF450A0A.toInt(), 0xFF7F1D1D.toInt(), 0xFFDC2626.toInt(), 0xFFDC2626.toInt(), 0xFF7F1D1D.toInt(), 0xFF450A0A.toInt(), 0xFF1F1215.toInt()),
        intArrayOf(0xFF450A0A.toInt(), 0xFF7F1D1D.toInt(), 0xFFEF4444.toInt(), 0xFFF87171.toInt(), 0xFFEF4444.toInt(), 0xFFDC2626.toInt(), 0xFF7F1D1D.toInt(), 0xFF450A0A.toInt()),
        intArrayOf(0xFF7F1D1D.toInt(), 0xFFEF4444.toInt(), 0xFFFCA5A5.toInt(), 0xFFFECACA.toInt(), 0xFFF87171.toInt(), 0xFFEF4444.toInt(), 0xFFDC2626.toInt(), 0xFF7F1D1D.toInt()),
        intArrayOf(0xFFDC2626.toInt(), 0xFFF87171.toInt(), 0xFFFECACA.toInt(), 0xFFFFFFFF.toInt(), 0xFFFCA5A5.toInt(), 0xFFF87171.toInt(), 0xFFEF4444.toInt(), 0xFFDC2626.toInt()),
        intArrayOf(0xFFDC2626.toInt(), 0xFFEF4444.toInt(), 0xFFF87171.toInt(), 0xFFFCA5A5.toInt(), 0xFFEF4444.toInt(), 0xFFDC2626.toInt(), 0xFFB91C1C.toInt(), 0xFF991B1B.toInt()),
        intArrayOf(0xFF7F1D1D.toInt(), 0xFFDC2626.toInt(), 0xFFEF4444.toInt(), 0xFFDC2626.toInt(), 0xFFB91C1C.toInt(), 0xFF991B1B.toInt(), 0xFF7F1D1D.toInt(), 0xFF450A0A.toInt()),
        intArrayOf(0xFF450A0A.toInt(), 0xFF7F1D1D.toInt(), 0xFFB91C1C.toInt(), 0xFF991B1B.toInt(), 0xFF7F1D1D.toInt(), 0xFF450A0A.toInt(), 0xFF450A0A.toInt(), 0xFF1F1215.toInt()),
        intArrayOf(0xFF1F1215.toInt(), 0xFF450A0A.toInt(), 0xFF450A0A.toInt(), 0xFF7F1D1D.toInt(), 0xFF450A0A.toInt(), 0xFF1F1215.toInt(), 0xFF1F1215.toInt(), 0xFF1F1215.toInt())
    )
}

private val pixelArtBitmapCache =
    java.util.EnumMap<MinecraftArtKind, androidx.compose.ui.graphics.ImageBitmap>(MinecraftArtKind::class.java)

private fun cachedPixelArtBitmap(kind: MinecraftArtKind): androidx.compose.ui.graphics.ImageBitmap {
    synchronized(pixelArtBitmapCache) {
        return pixelArtBitmapCache.getOrPut(kind) {
            createMinecraftPixelArtBitmap(kind, sizePx = 64).asImageBitmap()
        }
    }
}

@Composable
fun MinecraftPixelArtIcon(
    kind: MinecraftArtKind,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(kind) { cachedPixelArtBitmap(kind) }
    val glass = HorizonGlass
    androidx.compose.foundation.Image(
        bitmap = bitmap,
        contentDescription = null,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, glass.specularTop, RoundedCornerShape(7.dp))
    )
}

@Composable
fun CoreBrandLogo(
    core: com.mineserve.mobile.data.ServerCore,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val officialBitmap = remember(core) { OfficialCoreLogos.bitmapFor(core) }
    val borderColor = remember(core) {
        when (core) {
            com.mineserve.mobile.data.ServerCore.Pumpkin -> Color(0xFFFB923C)
            com.mineserve.mobile.data.ServerCore.Paper -> Color(0xFF38BDF8)
            com.mineserve.mobile.data.ServerCore.Purpur -> Color(0xFFC084FC)
            com.mineserve.mobile.data.ServerCore.Fabric -> Color(0xFFD6D3C4)
            com.mineserve.mobile.data.ServerCore.Forge -> Color(0xFFF59E0B)
            com.mineserve.mobile.data.ServerCore.NeoForge -> Color(0xFFFB923C)
            com.mineserve.mobile.data.ServerCore.Spigot -> Color(0xFFFBBF24)
            com.mineserve.mobile.data.ServerCore.CraftBukkit -> Color(0xFF60A5FA)
            com.mineserve.mobile.data.ServerCore.Vanilla -> Color(0xFFEF4444)
            com.mineserve.mobile.data.ServerCore.Quilt -> Color(0xFFF472B6)
            com.mineserve.mobile.data.ServerCore.Leaves,
            com.mineserve.mobile.data.ServerCore.Leaf -> Color(0xFF34D399)
            com.mineserve.mobile.data.ServerCore.Velocity -> Color(0xFF22D3EE)
            com.mineserve.mobile.data.ServerCore.BungeeCord -> Color(0xFFFACC15)
            com.mineserve.mobile.data.ServerCore.PowerNukkitX -> Color(0xFF4ADE80)
            com.mineserve.mobile.data.ServerCore.PocketMine -> Color(0xFF38BDF8)
            com.mineserve.mobile.data.ServerCore.Allay -> Color(0xFF67E8F9)
            com.mineserve.mobile.data.ServerCore.Unknown -> Color(0xFF64748B)
        }
    }
    if (core == com.mineserve.mobile.data.ServerCore.Pumpkin) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFEA580C), Color(0xFF9A3412))
                    )
                )
                .border(1.4.dp, Color(0xFFFB923C), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🦀",
                fontSize = (size.value * 0.52f).sp
            )
        }
    } else if (officialBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = officialBitmap,
            contentDescription = core.displayName,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .border(1.2.dp, borderColor.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
        )
    } else {
        MinecraftPixelArtIcon(
            kind = MinecraftArtKind.GRASS,
            size = size,
            modifier = modifier
        )
    }
}

fun createMinecraftPixelArtBitmap(kind: MinecraftArtKind, sizePx: Int = 64): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val grid = pixelGridFor(kind)
    val cell = sizePx / 8
    for (y in 0 until sizePx) {
        val r = (y / cell).coerceIn(0, 7)
        for (x in 0 until sizePx) {
            val c = (x / cell).coerceIn(0, 7)
            bmp.setPixel(x, y, grid[r][c])
        }
    }
    return bmp
}

private fun inferCardArtKind(title: String): MinecraftArtKind {
    val lower = title.lowercase()
    return when {
        lower.contains("mod") || lower.contains("rương") || lower.contains("sao lưu") || lower.contains("backup") -> MinecraftArtKind.CHEST
        lower.contains("lệnh") || lower.contains("console") || lower.contains("cấu hình") || lower.contains("core") -> MinecraftArtKind.COMMAND_BLOCK
        lower.contains("mạng") || lower.contains("playit") || lower.contains("ip") || lower.contains("địa chỉ") -> MinecraftArtKind.ENDER_EYE
        lower.contains("người chơi") || lower.contains("pvp") || lower.contains("op") || lower.contains("quyền") -> MinecraftArtKind.DIAMOND_SWORD
        lower.contains("sự cố") || lower.contains("crash") || lower.contains("ram") || lower.contains("java") -> MinecraftArtKind.REDSTONE
        lower.contains("creeper") || lower.contains("minehost") -> MinecraftArtKind.CREEPER
        else -> MinecraftArtKind.GRASS
    }
}

/**
 * Thẻ kính trong suốt Pure Horizon Glass (McCard):
 * Dùng `horizonGlassPlate` chuẩn 20dp + viền ánh sáng đỉnh 1dp.
 */
@Composable
fun McCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    val artKind = remember(title) { inferCardArtKind(title) }
    val glass = HorizonGlass
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = McSpacing.Page,
                vertical = if (compact) McSpacing.CardGapCompact else McSpacing.CardGap
            )
            .horizonGlassPlate(
                shape = McShape.Card,
                containerColor = glass.glassCard,
                borderColor = glass.hairline
            )
    ) {
        Column(
            Modifier.padding(
                if (compact) McSpacing.CardInnerCompact else McSpacing.CardInner
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    MinecraftPixelArtIcon(kind = artKind, size = 20.dp)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing.invoke()
                }
            }
            Spacer(
                Modifier.height(
                    if (compact) McSpacing.CardHeaderCompact else McSpacing.CardHeader
                )
            )
            content()
        }
    }
}

private data class StepVisual(
    val bg: Color,
    val fg: Color,
    val border: Color,
    val symbol: String
)

/**
 * Hàng bước khởi tạo / cài đặt với huy hiệu kính phát sáng.
 */
@Composable
fun StepRow(
    name: String,
    status: StepStatus,
    tag: String
) {
    val glass = HorizonGlass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .clearAndSetSemantics {
                contentDescription = "$name, $tag"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val visual = when (status) {
            StepStatus.Done -> StepVisual(glass.liveGreenSoft, glass.liveGreen, glass.liveGreen.copy(alpha = 0.5f), "✓")
            StepStatus.Active -> StepVisual(glass.accentSoft, glass.accent, glass.accent.copy(alpha = 0.55f), "•")
            StepStatus.Wait -> StepVisual(glass.glassRow, Muted, glass.hairline, "○")
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(visual.bg)
                .border(1.dp, visual.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(visual.symbol, color = visual.fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        Text(
            name,
            color = if (status == StepStatus.Wait) Muted else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            tag,
            color = Muted,
            style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle)
        )
    }
}

/**
 * Thanh tiến trình kính mờ với dải chuyển sắc Horizon.
 */
@Composable
fun ProgressTrack(percent: Int) {
    val glass = HorizonGlass
    val ratio = (percent.coerceIn(0, 100)) / 100f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(McShape.Pill)
            .background(glass.glassInput)
            .border(0.8.dp, glass.hairline, McShape.Pill)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio)
                .height(8.dp)
                .clip(McShape.Pill)
                .background(Brush.horizontalGradient(listOf(glass.accent, glass.skyCyan)))
        )
    }
}

/**
 * Nút chọn dạng viên thuốc kính (SegPill).
 */
@Composable
fun SegPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    unselectedBackground: Color = HorizonGlass.glassRow,
    onClick: () -> Unit
) {
    val glass = HorizonGlass
    val shape = McShape.Chip
    val pillModifier = if (selected) {
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary, shape)
            .border(1.dp, glass.specularTop, shape)
    } else {
        modifier
            .heightIn(min = 48.dp)
            .horizonGlassPlate(
                shape = shape,
                containerColor = unselectedBackground,
                borderColor = glass.hairline
            )
    }
    Box(
        modifier = pillModifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Ô nhập liệu chống giật (300ms debounce) tích hợp nền kính mờ `glassInput`.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun DebouncedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    sanitize: ((String) -> String)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    shape: Shape = McShape.Control
) {
    val glass = HorizonGlass
    var text by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(value) {
        if (!editing && text != value) text = value
    }

    LaunchedEffect(text) {
        if (text == value) return@LaunchedEffect
        delay(300)
        if (text != value) {
            onValueChange(text)
            editing = false
        }
    }

    LaunchedEffect(isFocused) {
        if (!isFocused && text != value) {
            onValueChange(text)
            editing = false
        }
        if (isFocused) {
            delay(100)
            bringIntoViewRequester.bringIntoView()
        }
    }

    val latestValue by rememberUpdatedState(value)
    DisposableEffect(Unit) {
        onDispose {
            if (text != latestValue) onValueChange(text)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            val next = sanitize?.invoke(newText) ?: newText
            if (next != text) {
                text = next
                editing = true
            }
        },
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { isFocused = it.isFocused },
        enabled = enabled,
        singleLine = singleLine,
        label = label,
        placeholder = placeholder,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        textStyle = textStyle,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = glass.glassInput,
            unfocusedContainerColor = glass.glassInput,
            disabledContainerColor = glass.glassRow.copy(alpha = 0.45f),
            focusedBorderColor = glass.accent,
            unfocusedBorderColor = glass.hairline
        )
    )
}

/**
 * Viên thuốc trạng thái kính mờ phát sáng với chữ số Tabular (`tnum`).
 */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val glass = HorizonGlass
    val hasLeadingSymbol = remember(text) {
        text.any { Character.getType(it) == Character.OTHER_SYMBOL.toInt() }
    }
    Row(
        modifier = modifier
            .clip(McShape.Pill)
            .background(color.copy(alpha = if (glass.isLight) 0.14f else 0.18f), McShape.Pill)
            .border(1.dp, color.copy(alpha = 0.42f), McShape.Pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (!hasLeadingSymbol) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium.merge(HorizonTabularStyle),
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Hàng hiển thị & sao chép địa chỉ LAN / Tunnel với mặt kính mờ và số Tabular.
 */
@Composable
fun AddressCopyRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    icon: ImageVector = Icons.Outlined.Public,
    accent: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = HorizonGlass.glassRow,
    borderColor: Color? = null
) {
    val glass = HorizonGlass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = containerColor,
                borderColor = borderColor ?: glass.hairline
            )
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(McIcon.Action))
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface,
                style = HorizonTabularStyle.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (value.isNotBlank()) {
            IconButton(onClick = onCopy, modifier = Modifier.size(McIcon.Touch)) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.s388),
                    tint = accent,
                    modifier = Modifier.size(McIcon.Item)
                )
            }
        }
    }
}

/**
 * Một dòng thông số chạy thực tế dùng chữ số Tabular không nhảy giật.
 */
@Composable
fun StatLine(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(McIcon.Inline))
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            color = Muted,
            style = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle)
        )
    }
}

/**
 * Thẻ chú thích thông tin nhẹ nhàng bằng kính mờ.
 */
@Composable
fun HintNote(text: String, icon: ImageVector = Icons.Outlined.Info) {
    val glass = HorizonGlass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = glass.hairline
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = glass.accent,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(McIcon.Inline)
        )
        Spacer(Modifier.size(8.dp))
        Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Thẻ cảnh báo nổi bật với viền kính Coral phát sáng dịu mắt.
 */
@Composable
fun WarnNote(text: String) {
    val glass = HorizonGlass
    val fgColor = if (glass.isLight) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.dangerCoralSoft,
                borderColor = glass.dangerCoral.copy(alpha = 0.48f)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = glass.dangerCoral,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(McIcon.Inline)
        )
        Spacer(Modifier.size(8.dp))
        Text(text, color = fgColor, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val QQ_GROUP_URL =
    "https://github.com/buiduchuy2010qn-prog/minehost-android/issues"

private const val QQ_GROUP_NUMBER = "github.com/buiduchuy2010qn-prog/minehost-android"

private fun openQqGroup(context: android.content.Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP_URL)))
    } catch (_: Exception) {
    }
}

@Composable
fun QqGroupCard() {
    val context = LocalContext.current
    McCard(title = stringResource(R.string.s1051)) {
        Text(
            stringResource(R.string.s1052),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.s1053),
            color = Muted,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { openQqGroup(context) },
            shape = McShape.Control,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(
                stringResource(R.string.s1054),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun QqGroupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val glass = HorizonGlass

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = McShape.Dialog,
        containerColor = glass.glassChrome,
        tonalElevation = 0.dp,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_qq_penguin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                stringResource(R.string.s1051),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.s1053),
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.glassRow,
                            borderColor = glass.hairline
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.qq_group_label),
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            QQ_GROUP_NUMBER,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(QQ_GROUP_NUMBER)) }) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.qq_copy_group), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { openQqGroup(context) },
                shape = McShape.Control,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    stringResource(R.string.s1054),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.env_cancel), color = Muted)
            }
        }
    )
}

@Composable
fun RuntimeGroupHeader(
    title: String,
    note: String,
    expanded: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(McShape.Row)
            .clickable(enabled = enabled) { onToggle() }
            .padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall
            )
            Text(note, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(McIcon.Item)
        )
    }
}

/**
 * Thanh chuyển tab trượt mượt trên rãnh kính mờ `glassInput`.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    if (options.isEmpty()) return
    val glass = HorizonGlass
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val tabHeight = maxOf(48.dp, with(LocalDensity.current) { 36.sp.toDp() } + 12.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(tabHeight)
            .horizonGlassPlate(
                shape = McShape.Chip,
                containerColor = glass.glassInput,
                borderColor = glass.hairline
            )
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        val segmentWidth = maxWidth / options.size
        val highlightOffset by animateDpAsState(
            targetValue = segmentWidth * safeIndex,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "segmented-tabs-highlight"
        )
        Box(
            modifier = Modifier
                .offset(x = highlightOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary)
                .border(1.dp, glass.specularTop, RoundedCornerShape(11.dp))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val isSelected = index == safeIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.Tab,
                            onClick = { onSelect(index) }
                        )
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ProotDistroPillSwitcher(
    selected: ProotDistro,
    enabled: Boolean,
    onSelect: (ProotDistro) -> Unit
) {
    val options = ProotDistro.entries
    if (options.isEmpty()) return
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    SegmentedTabs(
        options = options.map { it.shortName },
        selectedIndex = selectedIndex,
        enabled = enabled,
        onSelect = { index -> options.getOrNull(index)?.let(onSelect) }
    )
}

fun JavaVendor.groupTitleRes(): Int = when (this) {
    JavaVendor.Termux -> R.string.java_group_termux
    JavaVendor.Fcl -> R.string.java_group_fcl
    JavaVendor.Proot -> R.string.java_group_proot
}

fun JavaVendor.groupNoteRes(): Int = when (this) {
    JavaVendor.Termux -> R.string.java_group_termux_note
    JavaVendor.Fcl -> R.string.java_group_fcl_note
    JavaVendor.Proot -> R.string.java_group_proot_note
}

private fun javaVersionNoteRes(vendor: JavaVendor, version: JavaVersion): Int? =
    when {
        vendor == JavaVendor.Proot && version == JavaVersion.Java8 -> R.string.java_note_proot_java8
        vendor == JavaVendor.Proot -> R.string.java_note_proot_container
        else -> null
    }

@Composable
fun RuntimeVersionRow(
    label: String,
    note: String?,
    status: String?,
    statusColor: Color = Muted,
    selected: Boolean,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    val glass = HorizonGlass
    val rowModifier = if (selected) {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 48.dp)
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.liveGreenSoft,
                borderColor = glass.liveGreen,
                highlighted = false
            )
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 48.dp)
            .clip(McShape.Row)
    }
    Row(
        modifier = rowModifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.merge(HorizonTabularStyle),
                color = MaterialTheme.colorScheme.onSurface
            )
            note?.let {
                Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(McIcon.Inline),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            status?.let {
                Text(
                    it,
                    color = statusColor,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun JavaRuntimeGroupList(
    installed: Set<JavaRuntime>,
    selected: JavaRuntime,
    onSelect: (JavaRuntime) -> Unit,
    enabled: Boolean = true,
    inFlight: JavaRuntime? = null,
    expandedVendors: Set<JavaVendor> = emptySet(),
    onToggleVendor: (JavaVendor) -> Unit = {},
    prootDistro: ProotDistro = ProotDistro.Default,
    onDistroChange: (ProotDistro) -> Unit = {}
) {
    JavaVendor.entries.forEach { vendor ->
        val expanded = vendor in expandedVendors
        val note = buildString {
            append(stringResource(vendor.groupNoteRes()))
            if (!expanded && selected.vendor == vendor) {
                append(" · ")
                append(stringResource(R.string.java_group_selected, selected.version.displayName))
            }
        }
        RuntimeGroupHeader(
            title = stringResource(vendor.groupTitleRes()),
            note = note,
            expanded = expanded,
            onToggle = { onToggleVendor(vendor) }
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(tween(220)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
        ) {
            Column {
                if (vendor == JavaVendor.Proot) {
                    Spacer(Modifier.height(6.dp))
                    ProotDistroPillSwitcher(
                        selected = prootDistro,
                        enabled = enabled,
                        onSelect = onDistroChange
                    )
                    Spacer(Modifier.height(2.dp))
                }
                val versions = vendor.versions
                versions.forEach { version ->
                    val runtime = JavaRuntime(vendor, version)
                    val isInstalled = runtime in installed
                    val unavailable = vendor == JavaVendor.Proot &&
                        version !in prootDistro.availableJavaVersions
                    RuntimeVersionRow(
                        label = version.displayName,
                        note = javaVersionNoteRes(vendor, version)?.let { stringResource(it) },
                        status = when {
                            unavailable -> stringResource(R.string.java_note_proot_unavailable)
                            isInstalled -> stringResource(R.string.dash_java_installed)
                            else -> stringResource(R.string.env_java_not_installed)
                        },
                        statusColor = if (isInstalled && !unavailable) HorizonGlass.liveGreen else Muted,
                        selected = runtime == selected,
                        busy = runtime == inFlight,
                        onClick = { if (enabled && !unavailable) onSelect(runtime) }
                    )
                }
            }
        }
    }
}

suspend fun SnackbarHostState.showInstantSnackbar(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(
        message = message,
        duration = SnackbarDuration.Short
    )
}

@Composable
fun SwipeableInstantSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val glass = HorizonGlass
    val currentData = hostState.currentSnackbarData
    LaunchedEffect(currentData) {
        if (currentData != null) {
            delay(1650L)
            runCatching { currentData.dismiss() }
        }
    }
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        var dragOffsetX by remember(data) { mutableFloatStateOf(0f) }
        val isError = data.visuals.message.contains("Lỗi", ignoreCase = true) ||
            data.visuals.message.contains("thất bại", ignoreCase = true) ||
            data.visuals.message.contains("⚠️")
        val accentColor = if (isError) glass.dangerCoral else glass.liveGreen

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
                    .pointerInput(data) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(dragOffsetX) > 90f) {
                                    data.dismiss()
                                } else {
                                    dragOffsetX = 0f
                                }
                            },
                            onDragCancel = { dragOffsetX = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount
                                if (abs(dragOffsetX) > 180f) {
                                    data.dismiss()
                                }
                            }
                        )
                    }
                    .horizonGlassPlate(
                        shape = RoundedCornerShape(18.dp),
                        containerColor = glass.glassChrome,
                        borderColor = accentColor.copy(alpha = 0.65f)
                    )
                    .clickable { data.dismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinecraftPixelArtIcon(
                        kind = if (isError) MinecraftArtKind.REDSTONE else MinecraftArtKind.GRASS,
                        size = 18.dp
                    )
                    Text(
                        text = data.visuals.message,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(
                        onClick = { data.dismiss() },
                        modifier = Modifier.size(McIcon.Touch)
                    ) {
                        Text("✕", color = accentColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
