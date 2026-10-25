package com.mineserve.mobile.ui

// 性能修改理由：输入过程使用本地状态，避免异步保存回读打断输入法的光标位置。
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.res.stringResource
import com.mineserve.mobile.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.data.JavaRuntime
import com.mineserve.mobile.data.JavaVendor
import com.mineserve.mobile.data.JavaVersion
import com.mineserve.mobile.data.ProotDistro
import com.mineserve.mobile.data.ServerState
import com.mineserve.mobile.data.StepStatus
import kotlinx.coroutines.delay
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.FieldBg
import com.mineserve.mobile.ui.theme.FieldGray
import com.mineserve.mobile.ui.theme.IndigoSoft
import com.mineserve.mobile.ui.theme.LightIndigo
import com.mineserve.mobile.ui.theme.LightIndigoDark
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.MintBright
import com.mineserve.mobile.ui.theme.MintSoft
import com.mineserve.mobile.ui.theme.Muted

/**
 * 顶部 Header：参考界面 eyebrow + h1。
 * 默认以白色背景覆盖状态栏区域（配合全屏），内容避开状态栏文字；
 * 若该页面顶部已有返回栏承接状态栏避让，可传 statusBarPadding = false。
 */
@Composable
fun HeaderBlock(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    statusBarPadding: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    if (LocalMineHostChrome.current) {
        Column(modifier.padding(horizontal = McSpacing.Page, vertical = 8.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.bodyMedium, color = Muted)
            trailing?.invoke()
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
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
    }
}

/** 子页面统一返回栏：白底延伸到状态栏后面（状态栏透明）+ 返回按钮 + 标题 */
@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = McSpacing.Page, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(McIcon.Touch)) {
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
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }
    }
}

/** 统一空状态提示：图标 + 灰字文案，垂直居中 */
@Composable
fun EmptyHint(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = Muted, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Hero 卡片：渐变背景 + 状态行 + 紧凑指标行（TPS / 在线 / 进程内存 / 运行时长）
 */
@Composable
fun HeroBlock(
    state: ServerState,
    coreLabel: String,
    cpuPercent: Int? = null,
    /**
     * 服务端 -Xmx 上限（MB）。内存进度条用它作分母，与 [ServerState.usedMemoryMb] 一起
     * 决定进度比例；为 null/0 时进度条按 0 处理，只显示数值文本。
     */
    maxHeapMb: Int? = null,
    onlineModeEnabled: Boolean = false,
    /**
     * 服务端配置文件里 `white-list` 是否为 true（PNX 对应 pnx.yml 的 `settings.allowList`）。
     *
     * 白名单开着时必须在概览页看得见：它决定了「谁能进服」，而玩家的反馈只会是
     * 一句 `You are not white-listed`——服务器看起来一切正常，人却进不来。
     */
    whitelistEnabled: Boolean = false,
    isRefreshing: Boolean = false,
    lastRefreshAtMs: Long = 0L,
    /**
     * 跳过「启动中」阶段展示（见 [com.mineserve.mobile.data.McConfig.skipStartupProgress]）。
     *
     * true 时：不显示「正在启动服务端 · xxx」与阶段进度条，状态行直接按运行中呈现。
     * 只影响呈现，[ServerState.startupPhase] 本身照常推进。
     */
    skipStartupProgress: Boolean = false,
    onRefresh: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = McSpacing.Page, vertical = 8.dp)
            .clip(McShape.Card)
            .background(
                Brush.linearGradient(colors = listOf(LightIndigo, LightIndigoDark))
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            // 启动中：isRunning 且尚未完成启动（runningSinceMs == 0）。
            // 用户开了「跳过启动中进度」就当作已经就绪，不再暴露中间态。
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
                        size = 22.dp
                    )
                    Spacer(Modifier.size(8.dp))
                    // 状态指示点：启动中橙色，运行中亮绿，未运行半透明白
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isStarting -> Color(0xFFFFA726)
                                    state.isRunning -> MintBright
                                    else -> Color.White.copy(alpha = 0.5f)
                                }
                            )
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        when {
                            isStarting -> stringResource(R.string.hero_starting, coreLabel)
                            state.isRunning -> stringResource(R.string.hero_running, coreLabel)
                            else -> stringResource(R.string.hero_stopped)
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onRefresh != null) {
                    // 刷新中显示旋转进度圈，替代静态图标——
                    // 采集本身很快，若没有这层反馈，用户无法区分「没反应」和「刷了但值没变」。
                    if (isRefreshing) {
                        Box(
                            modifier = Modifier.size(McIcon.TouchCompact),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(McIcon.Action),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.9f)
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
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(McIcon.Action)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                HeroStat(
                    if (state.isRunning) String.format("%.1f", state.tps) else "--",
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
            // CPU 与内存同为水平进度条，各占一行；CPU 是整机读数，未运行服务端时也照常显示。
            Spacer(Modifier.height(8.dp))
            CpuMeterBar(percent = cpuPercent)
            Spacer(Modifier.height(8.dp))
            MemoryBar(
                usedMb = if (state.isRunning) state.usedMemoryMb else 0L,
                maxMb = maxHeapMb?.toLong() ?: 0L,
                running = state.isRunning
            )
            // 手动刷新完成反馈：明确告诉用户「已经更新过了」，
            // 即使各项数值本身没有变化，也不会被误认为按钮失效。
            if (!isRefreshing && lastRefreshAtMs > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Đã cập nhật · ${formatRefreshAgo(lastRefreshAtMs)}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
            if (onlineModeEnabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.hero_online_mode_warning),
                    color = Color(0xFFFF8A80),
                    fontSize = 14.sp
                )
            }
            if (whitelistEnabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.hero_whitelist_on),
                    color = Color(0xFFFFD180),
                    fontSize = 14.sp
                )
            }
            // 启动中提示：适配启动耗时较长的场景
            if (isStarting) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.startupPhase.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFC857),
                    trackColor = Color.White.copy(alpha = 0.2f)
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
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
                Text(
                    "Chuẩn bị môi trường · Tải thư viện · Khởi chạy Java · Nạp Core · Tạo thế giới · Mở mạng",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Hero 卡片里的内存水平进度条。
 *
 * - 进度 = 已用内存 / 设置的上限（[ServerState.usedMemoryMb] / `maxHeapMb`）；
 * - 最右侧显示「已使用内存 / 设置的上限内存」，如 `2.4 GB / 4 GB`；
 * - 上限未知或为 0 时进度条按 0 处理，只显示数值文本，避免出现除零或满格误导。
 */
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

/**
 * Hero 卡片里的 CPU 水平进度条（与内存同款外形）。
 *
 * 与内存条唯一的区别是右侧数值为百分比，且**不受服务端运行状态影响**：
 * CPU 是整机读数，服务端没跑也要显示当前占用。
 */
@Composable
private fun CpuMeterBar(percent: Int?) {
    val label = stringResource(R.string.hero_cpu)
    val value = percent?.coerceIn(0, 100)
    val ratio = (value ?: 0) / 100f
    HeroMeterBar(label = label, ratio = ratio, valueText = value?.let { "$it%" } ?: "--")
}

/**
 * Hero 卡片内「标签 + 水平进度条 + 右侧数值」的通用行。
 *
 * CPU 与内存共用同一外形（原 CPU 的环形条已按需求改为与内存一致的水平条）：
 * 圆角轨道 + 渐变填充，底色针对深色渐变卡片用半透明白，保证在靛蓝背景上仍可辨认。
 */
@Composable
private fun HeroMeterBar(label: String, ratio: Float, valueText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label, $valueText" }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                valueText,
                color = Color.White,
                fontSize = 14.sp,
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
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.22f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.linearGradient(listOf(MintBright, Mint)))
            )
        }
    }
}

/** 内存数值格式化：MB 输入，>=1024MB 用 GB 展示（保留一位小数），否则用 MB。 */
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
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp
        )
    }
}

/** 运行时长格式化：秒/分钟/小时 */
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

/**
 * 手动刷新的相对时间描述（「刚刚 / N 秒前 / N 分钟前」）。
 *
 * 存在的意义是让「刷新」这个动作**可被观察到**：采集带缓存，数值经常不变，
 * 光看数字无法判断按钮是否生效。
 */
private fun formatRefreshAgo(refreshAtMs: Long): String {
    val elapsedSec = ((System.currentTimeMillis() - refreshAtMs).coerceAtLeast(0L)) / 1000
    return when {
        elapsedSec < 5 -> "vừa xong"
        elapsedSec < 60 -> "${elapsedSec} giây trước"
        elapsedSec < 3600 -> "${elapsedSec / 60} phút trước"
        else -> "${elapsedSec / 3600} giờ trước"
    }
}

/**
 * 通用卡片容器：白底 + 1px 描边 + 极浅投影，圆角与页面边距取自全局令牌。
 *
 * 所有卡片共用同一圆角（[McShape.Card]）、同一边距（[McSpacing.Page]）与同一投影，
 * 这样不同页面上下滚动时卡片边缘始终在同一条竖线上，不会因为某个页面写了 14dp、
 * 另一个页面写了 20dp 而出现整体错位。
 */
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
    androidx.compose.foundation.Image(
        bitmap = bitmap,
        contentDescription = null,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
    )
}

/**
 * Logo thương hiệu chính thức (Official Brand Logo PNG 64x64) cho từng loại Core Minecraft:
 * Fabric, PaperMC, NeoForge, Forge, Purpur, PowerNukkitX, Spigot, CraftBukkit,
 * Quilt, Leaves, Leaf, Velocity, BungeeCord, PocketMine-MP, Allay, Vanilla.
 * Được giải mã 1 lần duy nhất vào bộ nhớ đệm EnumMap<ServerCore, ImageBitmap>, cuộn mượt 120 FPS (0 cấp phát bộ nhớ khi cuộn).
 */
@Composable
fun CoreBrandLogo(
    core: com.mineserve.mobile.data.ServerCore,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val officialBitmap = remember(core) { OfficialCoreLogos.bitmapFor(core) }
    val borderColor = remember(core) {
        when (core) {
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
    if (officialBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = officialBitmap,
            contentDescription = core.displayName,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(9.dp))
                .border(1.2.dp, borderColor.copy(alpha = 0.72f), RoundedCornerShape(9.dp))
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
 * Thẻ nội dung thống nhất: nền rõ ràng, tiêu đề vừa và khoảng cách thoáng.
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = McSpacing.Page,
                vertical = if (compact) McSpacing.CardGapCompact else McSpacing.CardGap
            ),
        shape = McShape.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
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
                        Spacer(Modifier.width(8.dp))
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
}

/**
 * 步骤行（参考界面 step-row）
 */
@Composable
fun StepRow(
    name: String,
    status: StepStatus,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .clearAndSetSemantics {
                contentDescription = "$name, $tag"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (bg, fg, text) = when (status) {
            StepStatus.Done -> Triple(MintSoft, MaterialTheme.colorScheme.onSecondaryContainer, "✓")
            StepStatus.Active -> Triple(IndigoSoft, MaterialTheme.colorScheme.onPrimaryContainer, "•")
            StepStatus.Wait -> Triple(FieldGray, Muted, "○")
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        Text(
            name,
            color = if (status == StepStatus.Wait) Muted else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(tag, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * 进度条（参考界面 progress-track）
 */
@Composable
fun ProgressTrack(percent: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(FieldGray)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent / 100f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/**
 * 圆角分段按钮：参考界面 .seg / .on
 */
@Composable
fun SegPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    unselectedBackground: Color = FieldGray,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(McShape.Chip)
            .background(if (selected) MaterialTheme.colorScheme.primary else unselectedBackground)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else Muted,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * 防抖输入框：本地即时编辑 + 300ms 防抖写回外部值，失焦时立即写回。
 *
 * 解决 TextField 直接绑定持久化 StateFlow（如 config）导致的输入延迟与
 * 快速输入文字顺序错乱问题：输入过程完全在本地状态上进行，停顿 300ms
 * 后才把结果回传（快速连续输入只回传最后一次），失焦时立即回传避免丢失。
 *
 * @param sanitize 可选输入清洗函数（如数字框只保留数字），每次输入后应用
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
    var text by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // 外部值同步：仅在非编辑状态时同步到本地。
    // 关键：防抖写回后外部 value 的回显更新有延迟，若此时用户已继续输入，
    // 无条件覆盖会把正在输入的内容重置回旧值（快速输入文字丢失/乱序）。
    LaunchedEffect(value) {
        if (!editing && text != value) text = value
    }

    // 输入停顿 300ms 后写回（连续输入只写回最后一次）
    LaunchedEffect(text) {
        if (text == value) return@LaunchedEffect
        delay(300)
        if (text != value) {
            onValueChange(text)
            editing = false
        }
    }

    // 失焦时立即写回，避免切换页面丢失最后输入
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

    // 组合移除（切页/销毁）时立即回传未写回内容，避免防抖窗口内输入丢失。
    // value 是普通参数、不是快照状态，DisposableEffect(Unit) 的闭包只会捕获首帧的值，
    // 因此用 rememberUpdatedState 取最新值作为比较基准，否则退出时会用旧文本覆盖已生效的新值。
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
            // 仅当清洗后文本真的变化时才进入编辑态：
            // 若清洗结果与原文本相同，LaunchedEffect(text) 不会重启，editing 会永久卡在 true，
            // 导致外部值同步失效、退出时把旧文本写回。
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
        shape = shape
    )
}

// ── EdgeCube 式网络状态组件 ────────────────────────────────────
//
// 借鉴 EdgeCube「网络映射页」的信息组织：状态用胶囊而不是纯文本，地址用带图标与
// 复制按钮的小卡片，运行统计在位时才出现，失败原因单独成条而不是塞进状态文字里。

/**
 * 状态胶囊：圆角 12dp + 12% alpha 同色底 + 同色文字。
 *
 * 语义色沿用项目既有的三档：运行中 [Mint]、进行中（启动/探测）[Coral]、其余 [Muted]。
 * 调用方负责按状态给色，本组件只负责画。
 */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(McShape.Chip)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * 公网/局域网地址展示条：图标 + 标题 + 等宽加粗地址 + 一键复制。
 *
 * [value] 为空时不显示复制按钮——没有可复制的内容时留一个禁用按钮只会让人反复点。
 */
@Composable
fun AddressCopyRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    icon: ImageVector = Icons.Outlined.Public,
    accent: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = FieldBg,
    borderColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(McShape.Row)
            .background(containerColor)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, McShape.Row)
                else Modifier
            )
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(McIcon.Action))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
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
 * 单行运行统计：小图标 + 说明文本。
 *
 * 只在隧道真正运行时才调用——未运行时不摆一排「0 B/s」占地方。
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
        Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 前提说明条：info 图标 + 说明文案。
 *
 * 用来讲清「这个功能只在什么条件下有效」，避免用户在不支持的环境下反复尝试。
 */
@Composable
fun HintNote(text: String, icon: ImageVector = Icons.Outlined.Info) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(McShape.Row)
            .background(FieldBg)
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(McIcon.Inline))
        Spacer(Modifier.size(8.dp))
        Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 失败/警告条：warning 图标 + 原因文案，用 Coral 系配色。
 *
 * 与 [HintNote] 分开是因为「当前出错了」和「使用前需知」的视觉权重不该一样。
 */
@Composable
fun WarnNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(McShape.Row)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp)
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(McIcon.Inline)
        )
        Spacer(Modifier.size(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Cộng đồng MineHost GitHub chính thức */
private const val QQ_GROUP_URL =
    "https://github.com/buiduchuy2010qn-prog/minehost-android/issues"

/** Địa chỉ repo cộng đồng (dùng để sao chép & hiển thị) */
private const val QQ_GROUP_NUMBER = "github.com/buiduchuy2010qn-prog/minehost-android"

/** 打开 QQ 加群页面；无浏览器/QQ 时静默失败 */
private fun openQqGroup(context: android.content.Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP_URL)))
    } catch (_: Exception) {
        // 无可用应用时静默
    }
}

/**
 * QQ 交流群入口卡片：显示群号并提供一键加群按钮。
 */
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                stringResource(R.string.s1054),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * QQ 交流群弹窗：概览页顶部企鹅图标点击后弹出。
 *
 * 显示群号，提供「复制群号」与「一键加群」两个操作。
 */
@Composable
fun QqGroupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = McShape.Dialog,
        containerColor = MaterialTheme.colorScheme.surface,
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
                // 群号展示块
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FieldGray)
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

// ══════════════════════════════════════════════════════════════
// Java 运行环境：来源分组列表
//
// 依赖管理页的 Java 板块与概览页「运行环境」弹窗共用这一套渲染，
// 分组与版本都直接来自 JavaVendor.entries × vendor.versions，
// 不在这里另抄一份清单 —— 否则两处会各自漂移出「弹窗里能选、依赖页里没有」的错位。
// ══════════════════════════════════════════════════════════════

/**
 * 来源分组标题：一行标题 + 一句来源说明，右侧折叠箭头。整行可点，点击切换展开。
 *
 * 字体、颜色、圆角与间距沿用原有取值，只多了一个箭头与点击态 ——
 * 折叠控件不应该看起来像另一个东西。
 *
 * 折叠的意义：三个来源的版本行加起来 11 条，默认全展开会把「卸载 / 重装 / 安装」
 * 三键挤到屏幕外，而这三个按钮才是这个板块的主要操作。
 */
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
            .padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 2.dp),
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
            // 展开时朝上、折叠时朝下：箭头指向「点下去会发生什么」的相反方向是常态，
            // 但这里用「当前状态」语义更直观 —— 朝下代表内容被收起。
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(McIcon.Item)
        )
    }
}

/**
 * 分段切换控件：等宽分段 + 滑动高亮块。
 *
 * 项目里原先有四套「同义不同形」的切换控件：插件页与玩家页各写了一遍
 * 「淡蓝轨道 + 蓝色选中块」，环境管理页用 [FilterChip]，PRoot 发行版用另一份
 * 带动画的高亮块。这里收敛成一份实现，所有分段切换都由它渲染，
 * 于是轨道高度、圆角、字号、选中色、切换动画在任意页面都一致。
 *
 * 选中块用 260ms 缓动**滑动**到目标位置而不是瞬移：切换后往往伴随下方内容变化，
 * 有位移过程才容易把「我点了这里」和「下面变了」联系起来。
 *
 * @param options 分段文案（等宽排布，调用方自行带上数量等后缀）
 * @param selectedIndex 当前选中项下标，越界时按 0 处理
 * @param enabled 为假时整条置灰且不响应点击
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
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val tabHeight = maxOf(48.dp, with(LocalDensity.current) { 36.sp.toDp() } + 12.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(tabHeight)
            .clip(McShape.Chip)
            .background(FieldGray)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        val segmentWidth = maxWidth / options.size
        val highlightOffset by animateDpAsState(
            targetValue = segmentWidth * safeIndex,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "segmented-tabs-highlight"
        )
        // 高亮块：留 3dp 内边距，所以圆角比轨道小 3dp，视觉上正好同心
        Box(
            modifier = Modifier
                .offset(x = highlightOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary)
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
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Muted,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Ubuntu 发行版胶囊切换控件。
 *
 * 左侧 Ubuntu 20.04、右侧 Ubuntu 22.04；实现完全交给 [SegmentedTabs]，
 * 保证与插件分类、玩家标签等分段切换控件在高度、圆角、配色与滑动动画上完全一致。
 *
 * 版本里用短名（「Ubuntu 20.04」）：一半宽度放不下带代号的显示名。
 */
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
        // 胶囊里用短名（「Ubuntu 20.04」）：一半宽度放不下带代号的显示名
        options = options.map { it.shortName },
        selectedIndex = selectedIndex,
        enabled = enabled,
        onSelect = { index -> options.getOrNull(index)?.let(onSelect) }
    )
}

/** 来源的分组标题文案。 */
fun JavaVendor.groupTitleRes(): Int = when (this) {
    JavaVendor.Termux -> R.string.java_group_termux
    JavaVendor.Fcl -> R.string.java_group_fcl
    JavaVendor.Proot -> R.string.java_group_proot
}

/** 来源分组标题下的一句说明。 */
fun JavaVendor.groupNoteRes(): Int = when (this) {
    JavaVendor.Termux -> R.string.java_group_termux_note
    JavaVendor.Fcl -> R.string.java_group_fcl_note
    JavaVendor.Proot -> R.string.java_group_proot_note
}

/**
 * 版本行的一句补充说明；只有能力上确实有差异的版本才有。
 *
 * - Proot 的 Java 8：只有 focal 提供它，切到 jammy 后这一项会从列表里消失。
 *   不提示的话，用户会以为「装好的 Java 8 丢了」。
 * - Proot 的其他版本：容器内是 glibc 环境，IPv6 兼容层不生效，
 *   且能装哪些版本取决于所选发行版（见 [ProotDistro.availableJavaVersions]）。
 *
 * 注：Java 8 曾挂在 Termux 分组下（那时它已跑在容器里），现已归入 PRoot 分组，
 * 因此不再有「Termux + Java 8」的说明分支。
 */
private fun javaVersionNoteRes(vendor: JavaVendor, version: JavaVersion): Int? =
    when {
        vendor == JavaVendor.Proot && version == JavaVersion.Java8 -> R.string.java_note_proot_java8
        vendor == JavaVendor.Proot -> R.string.java_note_proot_container
        else -> null
    }

/**
 * 弹窗 / 列表里的单个运行环境版本行：选中态用 Mint 边框 + 淡底。
 *
 * [busy] 为真时右侧的状态文字换成进度圈 —— 只有正在下载解压的那一行会亮起来，
 * 否则用户不知道那个 37–62MB 的进度到底是给谁的。
 */
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(McShape.Row)
            .background(if (selected) MintSoft else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = McShape.Row
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
            )
            note?.let {
                Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(McIcon.Inline), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            status?.let {
                Text(it, color = statusColor, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Java 运行环境分组列表：三个来源各一段（分组标题 + 组内纯版本号）。
 *
 * @param selected 当前选中项，选中态由 [JavaRuntime]（来源 + 版本）决定，
 *   两套 JDK 的版本号完全重合，只比版本号会串台。
 * @param enabled 为假时不响应点击（安装中 / 服务器运行中）。
 * @param inFlight 正在安装的那一项，只在该行显示进度圈。
 * @param expandedVendors 当前展开的来源集合。**默认空集（全部折叠）**：
 *   三个来源共 11 条版本行，全展开会把「卸载 / 重装 / 安装」三键顶出屏幕，
 *   而这三个按钮才是本板块的主要操作。展开状态由调用方持有，
 *   因为它是界面状态，不该由数据层或 ViewModel 承担。
 * @param onToggleVendor 点击分组标题时切换该来源的展开状态。
 * @param prootDistro PRoot 分类当前选中的发行版。PRoot 分类**必须**按它过滤版本：
 *   `JavaVendor.Proot.versions` 是所有发行版的并集（8/11/17/21），但单个发行版
 *   只提供其中一部分（focal 无 21、jammy 无 8）。若照并集全列出来，
 *   用户会看到 "Java 21" 可点、点了却装不上，属于「列表里出现装不上的项」。
 * @param onDistroChange 切换 PRoot 发行版。必须在**同一次**写入里同时调整版本：
 *   两个发行版的可用版本不同，沿用旧版本会写出装不上的组合。
 */
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
        // 折叠时把「已选哪一份」写进说明行：三个分组默认全折叠，而下面的
        // 「卸载 / 重装 / 安装」三键作用于当前选中项 —— 不标出来的话，
        // 用户在折叠状态下根本看不出这三个按钮会作用到哪一份 Java 上。
        // 展开时不需要：选中的那一行有 Mint 高亮边框，已经足够显眼。
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
                    // 发行版切换放在版本行之前：它决定下面能列出哪些版本，
                    // 顺序反了会让人以为「换了发行版版本却没变」是 bug。
                    Spacer(Modifier.height(6.dp))
                    ProotDistroPillSwitcher(
                        selected = prootDistro,
                        enabled = enabled,
                        onSelect = onDistroChange
                    )
                    Spacer(Modifier.height(2.dp))
                }
                // PRoot 列全部候选版本（并集），其中当前发行版不提供的置灰 ——
                // 只看并集会让用户点到一个装不上的版本；只列可用版本又会让用户
                // 以为应用漏了某个版本。置灰同时满足「看得到」与「点不动」。
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
                        statusColor = if (isInstalled && !unavailable) MaterialTheme.colorScheme.secondary else Muted,
                        selected = runtime == selected,
                        busy = runtime == inFlight,
                        onClick = { if (enabled && !unavailable) onSelect(runtime) }
                    )
                }
            }
        }
    }
}

/**
 * Hiển thị thông báo phản hồi tức thì (0ms trễ khi spam nhiều cài đặt),
 * tự động hủy thông báo cũ ngay lập tức để hiện thông báo mới nhất.
 */
suspend fun androidx.compose.material3.SnackbarHostState.showInstantSnackbar(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(
        message = message,
        duration = androidx.compose.material3.SnackbarDuration.Short
    )
}

/**
 * Thanh thông báo phản hồi nhanh kiểu Minecraft HUD hỗ trợ:
 * 1) Vuốt ngang qua trái/phải để bỏ thông báo ngay lập tức
 * 2) Chạm vào thông báo hoặc nút ✕ để đóng ngay
 * 3) Tự động ẩn nhanh sau 2.2 giây và không bao giờ xếp hàng gây trễ khi bấm liên tục
 */
@Composable
fun SwipeableInstantSnackbarHost(
    hostState: androidx.compose.material3.SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val currentData = hostState.currentSnackbarData
    LaunchedEffect(currentData) {
        if (currentData != null) {
            delay(1650L)
            runCatching { currentData.dismiss() }
        }
    }
    androidx.compose.material3.SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        var dragOffsetX by remember(data) { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val isError = data.visuals.message.contains("Lỗi", ignoreCase = true) ||
            data.visuals.message.contains("thất bại", ignoreCase = true) ||
            data.visuals.message.contains("⚠️")
        val accentColor = if (isError) Coral else Mint

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
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
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF141E19).copy(alpha = 0.94f))
                    .border(1.dp, accentColor.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                    .clickable { data.dismiss() }
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinecraftPixelArtIcon(
                        kind = if (isError) MinecraftArtKind.REDSTONE else MinecraftArtKind.GRASS,
                        size = 16.dp
                    )
                    Text(
                        text = data.visuals.message,
                        color = Color(0xFFF1F8F3),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(
                        onClick = { data.dismiss() },
                        modifier = Modifier.size(McIcon.Touch)
                    ) {
                        Text("✕", color = accentColor, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

