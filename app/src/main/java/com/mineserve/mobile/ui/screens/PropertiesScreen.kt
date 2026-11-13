package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.R
import com.mineserve.mobile.data.JavaServerPerformance
import com.mineserve.mobile.ui.ApplyHorizonDialogBlur
import com.mineserve.mobile.ui.BackBar
import com.mineserve.mobile.ui.DebouncedTextField
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.HintNote
import com.mineserve.mobile.ui.LocalHorizonChromePadding
import com.mineserve.mobile.ui.LocalMineHostChrome
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegPill
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate

private enum class PropGroup(val labelRes: Int) { Basic(R.string.s841), Advanced(R.string.s842) }

private enum class PropCategory(val labelVi: String) {
    Essential("⭐ Thiết yếu"),
    World("🌍 Thế giới & Sinh vật"),
    Network("🌐 Mạng & Bảo mật"),
    Advanced("⚙️ Nâng cao"),
    All("📋 Tất cả")
}

private enum class PropType { Bool, Int, Text, Enum }

private data class PropertySpec(
    val key: String,
    val labelRes: Int,
    val descRes: Int = 0,
    val type: PropType = PropType.Text,
    val group: PropGroup = PropGroup.Advanced,
    val options: List<Pair<Int, String>> = emptyList(),
    val default: String = ""
)

private val ESSENTIAL_KEYS = setOf(
    "gamemode",
    "difficulty",
    "max-players",
    "view-distance",
    "simulation-distance",
    "online-mode",
    "pvp",
    "white-list",
    "spawn-protection",
    "allow-flight",
    "enable-command-block",
    "motd",
    "server-port",
    "hardcore",
    "force-gamemode"
)

private val WORLD_KEYS = setOf(
    "level-name",
    "level-seed",
    "level-type",
    "allow-nether",
    "spawn-animals",
    "spawn-monsters",
    "spawn-npcs",
    "generate-structures",
    "max-world-size",
    "generator-settings"
)

private val NETWORK_KEYS = setOf(
    "server-ip",
    "enforce-whitelist",
    "enable-status",
    "hide-online-players",
    "op-permission-level",
    "function-permission-level",
    "rate-limit",
    "require-resource-pack",
    "resource-pack",
    "player-idle-timeout",
    "network-compression-threshold",
    "max-tick-time",
    "max-chained-neighbor-updates"
)

private data class ViPropertyGuide(
    val titleVi: String,
    val descVi: String,
    val intPresets: List<Pair<String, String>> = emptyList()
)

private val VI_PROPERTY_GUIDES: Map<String, ViPropertyGuide> = mapOf(
    "gamemode" to ViPropertyGuide(
        titleVi = "🎮 Chế độ chơi mặc định",
        descVi = "Chế độ áp dụng cho người chơi mới vào phòng: Sinh tồn, Sáng tạo, Phiêu lưu hoặc Khán giả."
    ),
    "difficulty" to ViPropertyGuide(
        titleVi = "⚔️ Độ khó thế giới",
        descVi = "Quy định sát thương quái vật và độ đói. Chọn Bình yên (peaceful) để tắt hoàn toàn quái thù địch."
    ),
    "max-players" to ViPropertyGuide(
        titleVi = "👥 Số người chơi tối đa",
        descVi = "Giới hạn số lượng người chơi cùng lúc trong phòng (khuyên đặt 5–20 trên điện thoại để giữ 20 TPS).",
        intPresets = listOf("5" to "5 người", "10" to "10 người", "20" to "20 người", "50" to "50 người")
    ),
    "view-distance" to ViPropertyGuide(
        titleVi = "🔭 Tầm nhìn hiển thị (Chunk)",
        descVi = "Bán kính cảnh vật gửi tới người chơi (1 Chunk = 16 ô). Đặt 6–12 Chunk giúp máy mát và mượt.",
        intPresets = listOf("6" to "6 Chunk", "8" to "8 Chunk", "10" to "10 Chunk", "12" to "12 Chunk", "16" to "16 Chunk")
    ),
    "simulation-distance" to ViPropertyGuide(
        titleVi = "⚡ Khoảng cách mô phỏng AI & Redstone",
        descVi = "Bán kính quái vật di chuyển, cây lớn và mạch Redstone hoạt động. Giữ 4–8 Chunk giảm tải CPU rõ rệt.",
        intPresets = listOf("4" to "4 Chunk (Nhẹ)", "6" to "6 Chunk", "8" to "8 Chunk", "10" to "10 Chunk")
    ),
    "online-mode" to ViPropertyGuide(
        titleVi = "🔐 Xác thực Bản quyền Microsoft (online-mode)",
        descVi = "BẬT = chỉ tài khoản mua bản quyền mới vào được. TẮT = cho phép PojavLauncher, FCL, TLauncher / Cracked vào ngay."
    ),
    "pvp" to ViPropertyGuide(
        titleVi = "🗡️ Đánh nhau giữa người chơi (PvP)",
        descVi = "Cho phép người chơi gây sát thương lên nhau bằng vũ khí. Tắt đi nếu muốn chơi co-op hòa bình."
    ),
    "white-list" to ViPropertyGuide(
        titleVi = "📋 Danh sách trắng bảo vệ phòng (Whitelist)",
        descVi = "Khi BẬT, chỉ những người chơi có tên trong Whitelist (hoặc OP) mới được phép tham gia máy chủ."
    ),
    "enforce-whitelist" to ViPropertyGuide(
        titleVi = "🛡️ Cưỡng chế Whitelist ngay lập tức",
        descVi = "Tự động đẩy người không nằm trong Whitelist ra khỏi phòng ngay khi bạn tải lại danh sách trắng."
    ),
    "spawn-protection" to ViPropertyGuide(
        titleVi = "🏰 Bán kính khóa vùng Hồi sinh",
        descVi = "Số ô quanh điểm xuất hiện chỉ OP mới đập/xây được. Đặt 0 để bạn bè mới vào có thể chặt cây, xây nhà ngay.",
        intPresets = listOf("0" to "0 ô (Mở khóa)", "8" to "8 ô", "16" to "16 ô")
    ),
    "allow-flight" to ViPropertyGuide(
        titleVi = "🕊️ Cho phép Bay & Chống Kick nhầm",
        descVi = "Tránh bị máy chủ đuổi nhầm ('Flying is not enabled') khi mạng lag, cưỡi thú, dùng Elytra hoặc cài Mod bay."
    ),
    "enable-command-block" to ViPropertyGuide(
        titleVi = "🧱 Bật Khối Lệnh (Command Block)",
        descVi = "Cần BẬT khi chơi Map phiêu lưu, Parkour, Minigame hoặc hệ thống dịch chuyển tự động bằng lệnh."
    ),
    "motd" to ViPropertyGuide(
        titleVi = "✨ Dòng giới thiệu máy chủ (MOTD)",
        descVi = "Lời chào hiển thị bên dưới tên Server trong danh sách Multiplayer của người chơi."
    ),
    "server-port" to ViPropertyGuide(
        titleVi = "🌐 Cổng kết nối máy chủ (Port)",
        descVi = "Cổng mạng lắng nghe kết nối (Java mặc định 25565 • Bedrock mặc định 19132).",
        intPresets = listOf("25565" to "25565 (Mặc định)")
    ),
    "hardcore" to ViPropertyGuide(
        titleVi = "💀 Chế độ Siêu khó (Hardcore - 1 mạng)",
        descVi = "Người chơi chỉ có 1 mạng duy nhất; nếu chết sẽ chuyển sang chế độ Khán giả (Spectator)."
    ),
    "force-gamemode" to ViPropertyGuide(
        titleVi = "🔄 Ép về chế độ chơi mặc định khi vào lại",
        descVi = "Mỗi lần người chơi đăng nhập lại sẽ tự động đưa về chế độ chơi mặc định của Server."
    ),
    "level-name" to ViPropertyGuide(
        titleVi = "🗺️ Tên thư mục Thế giới (Map đang chạy)",
        descVi = "Tên thư mục Map bên trong Server (mặc định: world). Đổi tên mới sẽ tạo ra một thế giới mới."
    ),
    "level-seed" to ViPropertyGuide(
        titleVi = "🌱 Hạt giống địa hình (World Seed)",
        descVi = "Mã Seed để tạo thế giới mới theo ý muốn (chỉ áp dụng khi tạo thế giới lần đầu)."
    ),
    "level-type" to ViPropertyGuide(
        titleVi = "🏔️ Kiểu địa hình thế giới",
        descVi = "Chọn địa hình Chuẩn (Normal), Siêu phẳng (Flat), Quần xã lớn hoặc Khuếch đại."
    ),
    "allow-nether" to ViPropertyGuide(
        titleVi = "🔥 Cho phép cổng Địa Ngục (Nether)",
        descVi = "Cho phép người chơi thắp cổng Obsidian và dịch chuyển sang chiều không gian Nether."
    ),
    "spawn-animals" to ViPropertyGuide(
        titleVi = "🐑 Sinh sản động vật hiền lành",
        descVi = "Cho phép cừu, bò, gà, heo... tự nhiên xuất hiện trên thế giới."
    ),
    "spawn-monsters" to ViPropertyGuide(
        titleVi = "🧟 Sinh sản quái vật ban đêm",
        descVi = "Cho phép Zombie, Skeleton, Creeper... xuất hiện trong bóng tối hoặc hang động."
    ),
    "spawn-npcs" to ViPropertyGuide(
        titleVi = "👨‍🌾 Sinh sản Dân làng (Villager)",
        descVi = "Cho phép dân làng xuất hiện và trao đổi vật phẩm tại các ngôi làng."
    ),
    "generate-structures" to ViPropertyGuide(
        titleVi = "🏛️ Tạo công trình tự nhiên",
        descVi = "Sinh ra Làng mạc, Đền sa mạc, Pháo đài Nether, Stronghold khi mở rộng bản đồ."
    )
)

private val propertySpecs: List<PropertySpec> = listOf(
    PropertySpec("gamemode", R.string.s730, R.string.s849, PropType.Enum, PropGroup.Basic,
        listOf(R.string.s281 to "survival", R.string.s282 to "creative", R.string.s283 to "adventure", R.string.s284 to "spectator"), "survival"),
    PropertySpec("difficulty", R.string.s843, R.string.s844, PropType.Enum, PropGroup.Basic,
        listOf(R.string.s845 to "peaceful", R.string.s846 to "easy", R.string.s847 to "normal", R.string.s848 to "hard"), "easy"),
    PropertySpec("max-players", R.string.s855, R.string.s856, PropType.Int, PropGroup.Basic, default = "20"),
    PropertySpec("view-distance", R.string.s863, R.string.s864, PropType.Int, PropGroup.Basic, default = JavaServerPerformance.VIEW_DISTANCE.toString()),
    PropertySpec("simulation-distance", R.string.s865, R.string.s866, PropType.Int, PropGroup.Basic, default = JavaServerPerformance.SIMULATION_DISTANCE.toString()),
    PropertySpec("online-mode", R.string.s851, R.string.s852, PropType.Bool, PropGroup.Basic, default = "true"),
    PropertySpec("pvp", 0, R.string.s850, PropType.Bool, PropGroup.Basic, default = "true"),
    PropertySpec("white-list", R.string.s659, R.string.s703, PropType.Bool, PropGroup.Basic),
    PropertySpec("enforce-whitelist", R.string.s853, R.string.s854, PropType.Bool, PropGroup.Basic),
    PropertySpec("spawn-protection", R.string.s869, R.string.s870, PropType.Int, PropGroup.Basic, default = "16"),
    PropertySpec("allow-flight", R.string.s875, R.string.s876, PropType.Bool, PropGroup.Basic),
    PropertySpec("enable-command-block", R.string.s873, R.string.s874, PropType.Bool, PropGroup.Basic),
    PropertySpec("motd", R.string.s861, R.string.s862, PropType.Text, PropGroup.Basic, default = "MineHost VN - May chu Minecraft"),
    PropertySpec("server-port", R.string.s857, R.string.s858, PropType.Int, PropGroup.Basic, default = "25565"),
    PropertySpec("server-ip", R.string.s859, R.string.s860, PropType.Text, PropGroup.Basic),
    PropertySpec("max-world-size", R.string.s867, R.string.s868, PropType.Int, PropGroup.Basic, default = "29999984"),
    PropertySpec("hardcore", R.string.s871, R.string.s872, PropType.Bool, PropGroup.Basic),
    PropertySpec("allow-nether", R.string.s877, R.string.s878, PropType.Bool, PropGroup.Basic, default = "true"),
    PropertySpec("spawn-animals", R.string.s879, R.string.s880, PropType.Bool, PropGroup.Basic, default = "true"),
    PropertySpec("spawn-monsters", R.string.s881, R.string.s882, PropType.Bool, PropGroup.Basic, default = "true"),
    PropertySpec("spawn-npcs", R.string.s883, R.string.s884, PropType.Bool, PropGroup.Basic, default = "true"),
    PropertySpec("generate-structures", R.string.s885, R.string.s886, PropType.Bool, PropGroup.Basic, default = "true"),
    PropertySpec("level-name", R.string.s887, R.string.s888, PropType.Text, PropGroup.Basic, default = "world"),
    PropertySpec("level-seed", R.string.s889, R.string.s890, PropType.Text, PropGroup.Basic),
    PropertySpec("level-type", R.string.s891, R.string.s892, PropType.Enum, PropGroup.Basic,
        listOf(
            R.string.s847 to "minecraft:normal",
            R.string.s893 to "minecraft:flat",
            R.string.s894 to "minecraft:large_biomes",
            R.string.s895 to "minecraft:amplified",
            R.string.s896 to "minecraft:single_biome_surface"
        ), "minecraft:normal"),
    PropertySpec("force-gamemode", R.string.s897, R.string.s898, PropType.Bool, PropGroup.Basic),
    PropertySpec("max-tick-time", R.string.s899, R.string.s900, PropType.Int, PropGroup.Basic, default = "60000"),
    PropertySpec("enable-status", R.string.s901, R.string.s902, PropType.Bool, PropGroup.Basic, default = "true"),
    PropertySpec("hide-online-players", R.string.s903, R.string.s904, PropType.Bool, PropGroup.Basic),
    PropertySpec("op-permission-level", R.string.s905, R.string.s906, PropType.Int, PropGroup.Basic, default = "4"),
    PropertySpec("function-permission-level", R.string.s907, R.string.s908, PropType.Int, PropGroup.Basic, default = "2"),
    PropertySpec("rate-limit", R.string.s909, R.string.s910, PropType.Int, PropGroup.Basic),
    PropertySpec("require-resource-pack", R.string.s911, R.string.s912, PropType.Bool, PropGroup.Basic),
    PropertySpec("resource-pack", R.string.s913, R.string.s914, PropType.Text, PropGroup.Basic),
    PropertySpec("player-idle-timeout", R.string.s915, R.string.s916, PropType.Int, PropGroup.Basic),
    PropertySpec("network-compression-threshold", R.string.s917, R.string.s918, PropType.Int, PropGroup.Basic, default = "256"),
    PropertySpec("max-chained-neighbor-updates", R.string.s919, R.string.s920, PropType.Int, PropGroup.Basic, default = "1000000"),

    PropertySpec("accepts-transfers", R.string.s921, R.string.s922, PropType.Bool),
    PropertySpec("broadcast-console-to-ops", R.string.s923, R.string.s924, PropType.Bool, default = "true"),
    PropertySpec("broadcast-rcon-to-ops", R.string.s925, R.string.s926, PropType.Bool, default = "true"),
    PropertySpec("bug-report-link", R.string.s927, R.string.s928, PropType.Text),
    PropertySpec("chat-spam-threshold-seconds", R.string.s929, R.string.s930, PropType.Int, default = "10"),
    PropertySpec("command-spam-threshold-seconds", R.string.s931, R.string.s932, PropType.Int, default = "10"),
    PropertySpec("debug", R.string.s933, R.string.s934, PropType.Bool),
    PropertySpec("enable-code-of-conduct", R.string.s935, R.string.s936, PropType.Bool),
    PropertySpec("enable-jmx-monitoring", R.string.s937, R.string.s938, PropType.Bool),
    PropertySpec("enable-query", R.string.s939, R.string.s940, PropType.Bool),
    PropertySpec("enable-rcon", R.string.s941, R.string.s942, PropType.Bool),
    PropertySpec("entity-broadcast-range-percentage", R.string.s943, R.string.s944, PropType.Int, default = "100"),
    PropertySpec("generator-settings", R.string.s945, R.string.s946, PropType.Text, default = "{}"),
    PropertySpec("initial-disabled-packs", R.string.s947, R.string.s948, PropType.Text),
    PropertySpec("initial-enabled-packs", R.string.s949, R.string.s950, PropType.Text, default = "vanilla"),
    PropertySpec("log-ips", R.string.s951, R.string.s952, PropType.Bool, default = "true"),
    PropertySpec("management-server-allowed-origins", R.string.s953, R.string.s954, PropType.Text),
    PropertySpec("management-server-enabled", R.string.s955, R.string.s956, PropType.Bool),
    PropertySpec("management-server-host", R.string.s957, R.string.s958, PropType.Text, default = "localhost"),
    PropertySpec("management-server-port", R.string.s959, R.string.s960, PropType.Int),
    PropertySpec("management-server-secret", R.string.s961, R.string.s962, PropType.Text),
    PropertySpec("management-server-tls-enabled", R.string.s963, R.string.s964, PropType.Bool, default = "true"),
    PropertySpec("management-server-tls-keystore", R.string.s965, R.string.s966, PropType.Text),
    PropertySpec("management-server-tls-keystore-password", R.string.s967, R.string.s968, PropType.Text),
    PropertySpec("pause-when-empty-seconds", R.string.s969, R.string.s970, PropType.Int, default = "-1"),
    PropertySpec("prevent-proxy-connections", R.string.s971, R.string.s972, PropType.Bool),
    PropertySpec("query.port", R.string.s973, R.string.s974, PropType.Int, default = "25565"),
    PropertySpec("rcon.password", R.string.s975, R.string.s976, PropType.Text),
    PropertySpec("rcon.port", R.string.s977, R.string.s978, PropType.Int, default = "25575"),
    PropertySpec("region-file-compression", R.string.s979, R.string.s980, PropType.Text, default = "deflate"),
    PropertySpec("resource-pack-id", R.string.s981, R.string.s982, PropType.Text),
    PropertySpec("resource-pack-prompt", R.string.s983, R.string.s984, PropType.Text),
    PropertySpec("resource-pack-sha1", R.string.s985, R.string.s986, PropType.Text),
    PropertySpec("status-heartbeat-interval", R.string.s987, R.string.s988, PropType.Int),
    PropertySpec("sync-chunk-writes", R.string.s989, R.string.s990, PropType.Bool, default = "true"),
    PropertySpec("text-filtering-config", R.string.s991, R.string.s992, PropType.Text),
    PropertySpec("text-filtering-version", R.string.s993, R.string.s994, PropType.Int),
    PropertySpec("use-native-transport", R.string.s995, R.string.s996, PropType.Bool, default = "true")
)

private fun categoryOfSpec(spec: PropertySpec): PropCategory = when {
    spec.key in ESSENTIAL_KEYS -> PropCategory.Essential
    spec.key in WORLD_KEYS -> PropCategory.World
    spec.key in NETWORK_KEYS -> PropCategory.Network
    else -> PropCategory.Advanced
}

@Composable
private fun KeyBadge(rawKey: String) {
    val glass = HorizonGlass
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(glass.glassInput)
            .border(1.dp, glass.hairline, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = rawKey,
            fontFamily = FontFamily.Monospace,
            color = glass.accent,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewDistanceBoosterCard(
    props: Map<String, String>,
    showSimulationDistance: Boolean,
    onUpdateProps: (Map<String, String>) -> Unit,
    onApplyImmediately: (Int, Int?) -> Unit
) {
    val glass = HorizonGlass
    val currentView = (props["view-distance"]?.toIntOrNull() ?: JavaServerPerformance.VIEW_DISTANCE).coerceIn(4, 32)
    val currentSim = (props["simulation-distance"]?.toIntOrNull() ?: JavaServerPerformance.SIMULATION_DISTANCE).coerceIn(4, 16)
    val isCracked = props["online-mode"]?.trim()?.equals("false", ignoreCase = true) == true
    val isSecureOff = props["enforce-secure-profile"]?.trim()?.equals("false", ignoreCase = true) == true
    val crackedReady = isCracked && isSecureOff
    val blocks = currentView * 16

    McCard(title = if (showSimulationDistance) "🔭 Kết nối & cấu hình nhẹ 6 / 4 chunk" else "🔭 Kết nối & tầm nhìn nhẹ 6 chunk") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizonGlassPlate(
                    shape = McShape.Row,
                    containerColor = if (crackedReady) glass.liveGreenSoft else glass.dangerCoralSoft,
                    borderColor = if (crackedReady) glass.liveGreen.copy(alpha = 0.55f) else glass.dangerCoral.copy(alpha = 0.55f),
                    highlighted = true
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    "🔐 Chế độ tài khoản (Sửa lỗi 'Phiên chơi không hợp lệ'):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (crackedReady) "✅ Đã cho phép mọi tài khoản (Cracked / TLauncher / FCL & Bản quyền)"
                    else "⚠️ Đang bật Bản quyền (online-mode=true) — Máy Cracked sẽ bị báo lỗi 'Phiên chơi không hợp lệ'",
                    color = if (crackedReady) glass.liveGreen else glass.dangerCoral,
                    fontSize = 10.5.sp,
                    lineHeight = 14.5.sp
                )
            }
            SegPill(
                text = if (crackedReady) "🔓 Đang bật Cracked" else "🛠️ Bật Cracked ngay",
                selected = crackedReady,
                onClick = {
                    val enableCracked = !crackedReady
                    onUpdateProps(
                        props.toMutableMap().apply {
                            put("online-mode", (!enableCracked).toString())
                            put("enforce-secure-profile", (!enableCracked).toString())
                            if (enableCracked) {
                                put("allow-flight", "true")
                                put("spawn-protection", "0")
                            }
                        }
                    )
                }
            )
        }
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tầm nhìn hiển thị (view-distance):",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$currentView Chunk (~$blocks ô)",
                style = HorizonTabularStyle,
                color = glass.liveGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                JavaServerPerformance.VIEW_DISTANCE to "6 Chunk (Nhẹ mượt)",
                8 to "8 Chunk",
                10 to "10 Chunk",
                12 to "12 Chunk",
                16 to "16 Chunk"
            ).forEach { (v, label) ->
                SegPill(
                    text = label,
                    selected = currentView == v,
                    compact = true,
                    onClick = {
                        onUpdateProps(props.toMutableMap().apply { put("view-distance", v.toString()) })
                    }
                )
            }
        }

        if (showSimulationDistance) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Khoảng cách mô phỏng (simulation-distance):",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$currentSim Chunk",
                    style = HorizonTabularStyle,
                    color = glass.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    JavaServerPerformance.SIMULATION_DISTANCE to "4 Chunk (Giảm tải CPU)",
                    6 to "6 Chunk",
                    8 to "8 Chunk"
                ).forEach { (s, label) ->
                    SegPill(
                        text = label,
                        selected = currentSim == s,
                        compact = true,
                        onClick = {
                            onUpdateProps(props.toMutableMap().apply { put("simulation-distance", s.toString()) })
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HintNote(
            if (showSimulationDistance) "💡 Cấu hình nhẹ: tầm nhìn 6 chunk, mô phỏng 4 chunk. Nút bên dưới áp dụng 6/4; cấu hình đang lưu chỉ đổi khi bạn xác nhận. CPU, nhiệt độ, người chơi và mod vẫn ảnh hưởng TPS."
            else "💡 Áp dụng tầm nhìn 6 chunk. Core này không có mục mô phỏng Java. CPU, nhiệt độ, người chơi và plugin vẫn ảnh hưởng độ mượt."
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                onApplyImmediately(
                    JavaServerPerformance.VIEW_DISTANCE,
                    if (showSimulationDistance) JavaServerPerformance.SIMULATION_DISTANCE else null
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = McShape.Control,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
        ) {
            Text(
                if (showSimulationDistance) "⚡ Áp dụng cấu hình nhẹ 6 / 4 chunk" else "⚡ Áp dụng tầm nhìn 6 chunk",
                style = HorizonTabularStyle,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun JavaPropertiesScreen(vm: McViewModel, onBack: () -> Unit, showBackBar: Boolean = true) {
    val glass = HorizonGlass
    val chromePad = LocalHorizonChromePadding.current
    val sharedChrome = LocalMineHostChrome.current
    val loaded by vm.serverProperties.collectAsState()
    val propertiesExist by vm.serverPropertiesExist.collectAsState()
    val serverState by vm.serverState.collectAsState()
    val config by vm.config.collectAsState()
    val rustStatus by vm.rustEngineStatus.collectAsState()

    if (config.installedCores.isEmpty()) {
        PropertiesEmptyScreen(onBack, showBackBar)
        return
    }

    var props by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf(PropCategory.Essential) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(config.activeCoreName) { vm.loadServerProperties() }
    LaunchedEffect(loaded) { props = loaded }

    val header: @Composable () -> Unit = {
        if (showBackBar) BackBar(title = stringResource(R.string.s541), onBack = onBack)
    }

    if (propertiesExist == false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            header()
            HeaderBlock(
                eyebrow = stringResource(R.string.eyebrow_properties),
                title = stringResource(R.string.s997),
                statusBarPadding = !showBackBar
            )
            val activeCore = config.installedCores.find { it.name == config.activeCoreName }
            Text(
                stringResource(R.string.s998, activeCore?.name ?: stringResource(R.string.s999)),
                color = Muted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(horizontal = McSpacing.Page)
            )
            Spacer(Modifier.height(12.dp))
            PropertiesNotGeneratedCard(
                isRunning = serverState.isRunning,
                onStartServer = { vm.startServer() },
                onRefresh = { vm.loadServerProperties() },
                onCreateDefaultNow = { vm.generateDefaultServerProperties() }
            )
            Spacer(Modifier.height(chromePad.calculateBottomPadding() + 28.dp))
        }
        return
    }

    val trimmedQuery = searchQuery.trim()
    val filteredSpecs = remember(selectedCategory, trimmedQuery) {
        if (trimmedQuery.isNotBlank()) {
            propertySpecs.filter { spec ->
                val guide = VI_PROPERTY_GUIDES[spec.key]
                spec.key.contains(trimmedQuery, ignoreCase = true) ||
                    (guide?.titleVi?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    (guide?.descVi?.contains(trimmedQuery, ignoreCase = true) == true)
            }
        } else {
            when (selectedCategory) {
                PropCategory.All -> propertySpecs
                else -> propertySpecs.filter { categoryOfSpec(it) == selectedCategory }
            }
        }
    }

    val knownKeys = remember { propertySpecs.map { it.key }.toSet() }
    val unknownKeys = remember(props, selectedCategory, trimmedQuery) {
        val rawUnknown = props.keys.filter { it !in knownKeys }
        when {
            trimmedQuery.isNotBlank() -> rawUnknown.filter { it.contains(trimmedQuery, ignoreCase = true) }
            selectedCategory == PropCategory.Advanced || selectedCategory == PropCategory.All -> rawUnknown
            else -> emptyList()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            header()
            HeaderBlock(
                eyebrow = stringResource(R.string.eyebrow_properties),
                title = stringResource(R.string.s997),
                statusBarPadding = !showBackBar
            )

            val activeCore = config.installedCores.find { it.name == config.activeCoreName }
            Text(
                stringResource(R.string.s998, activeCore?.name ?: stringResource(R.string.s999)),
                color = Muted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(horizontal = McSpacing.Page)
            )
            Spacer(Modifier.height(8.dp))

            if (rustStatus.isNativeRustEnabled) {
                McCard(title = "🦀 Đồng bộ cấu hình Nhân Rust Thuần (pumpkin.toml)", compact = true) {
                    Text(
                        "Tệp cấu hình pumpkin.toml được tự động đồng bộ từ các khóa chính trong server.properties gồm: server-port, level-name, max-players và view-distance. Một số khóa đặc thù Java có thể chưa áp dụng trên Nhân Rust Thuần.",
                        color = glass.accent,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            ViewDistanceBoosterCard(
                props = props,
                showSimulationDistance = true,
                onUpdateProps = { props = it },
                onApplyImmediately = { viewDist, simDist ->
                    val updated = props.toMutableMap().apply {
                        put("view-distance", viewDist.toString())
                        if (simDist != null) put("simulation-distance", simDist.toString())
                    }
                    props = updated
                    vm.saveServerProperties(updated)
                    if (serverState.isRunning) showRestartDialog = true
                }
            )

            // ── Thanh phân nhóm Segmented Pills + Tìm kiếm nhanh server.properties ──
            McCard(
                title = "🎛️ Phân nhóm thông số server.properties",
                compact = true
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PropCategory.entries, key = { it.name }) { cat ->
                        SegPill(
                            text = cat.labelVi,
                            selected = selectedCategory == cat && trimmedQuery.isBlank(),
                            compact = true,
                            onClick = {
                                searchQuery = ""
                                selectedCategory = cat
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "🔍 Tìm nhanh khóa cấu hình (VD: gamemode, pvp, online-mode, view-distance...)",
                            fontSize = 11.5.sp
                        )
                    },
                    singleLine = true,
                    shape = McShape.Row,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = glass.glassInput,
                        unfocusedContainerColor = glass.glassInput,
                        focusedBorderColor = glass.accent,
                        unfocusedBorderColor = glass.hairline
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                "✕",
                                color = Muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { searchQuery = "" }
                                    .padding(8.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            McCard(
                title = if (trimmedQuery.isNotBlank()) {
                    "🔍 Kết quả tìm kiếm (${filteredSpecs.size + unknownKeys.size})"
                } else {
                    "${selectedCategory.labelVi} (${filteredSpecs.size + unknownKeys.size} thông số)"
                }
            ) {
                if (filteredSpecs.isEmpty() && unknownKeys.isEmpty()) {
                    Text(
                        "Không tìm thấy khóa cấu hình nào khớp từ khóa '$trimmedQuery'.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredSpecs.forEach { spec ->
                            renderProperty(spec, props) { v ->
                                props = props.toMutableMap().apply { put(spec.key, v) }
                            }
                        }
                        if (unknownKeys.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.s1000),
                                color = glass.accent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            unknownKeys.forEach { key ->
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
                                    KeyBadge(key)
                                    DebouncedTextField(
                                        value = props[key] ?: "",
                                        onValueChange = { v -> props = props.toMutableMap().apply { put(key, v) } },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(chromePad.calculateBottomPadding() + 92.dp))
        }

        val isSaved = props.isNotEmpty() && props == loaded
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val saveBottom = if (sharedChrome) {
            maxOf(chromePad.calculateBottomPadding(), imeBottom) + 10.dp
        } else {
            maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), imeBottom) + 10.dp
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = McSpacing.Page, end = McSpacing.Page, top = 6.dp, bottom = saveBottom)
                .horizonGlassPlate(
                    shape = RoundedCornerShape(22.dp),
                    containerColor = glass.glassChrome,
                    borderColor = glass.specularTop.copy(alpha = 0.42f)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Button(
                onClick = {
                    if (serverState.isRunning) {
                        showRestartDialog = true
                    } else {
                        vm.saveServerProperties(props)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSaved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                ),
                shape = McShape.Control,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    if (isSaved) "✅ Đã lưu cấu hình (Chạm để lưu lại)" else "💾 " + stringResource(R.string.s328),
                    color = if (isSaved) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            tonalElevation = 0.dp,
            title = {
                ApplyHorizonDialogBlur()
                Text(stringResource(R.string.s581), fontWeight = FontWeight.Bold)
            },
            text = { Text(stringResource(R.string.s1001)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    vm.saveServerProperties(props)
                }) { Text(stringResource(R.string.s1002), color = glass.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(R.string.s402), color = glass.dangerCoral)
                }
            }
        )
    }
}

private fun boolOf(props: Map<String, String>, key: String, default: Boolean): Boolean {
    return props[key]?.let { it.equals("true", ignoreCase = true) } ?: default
}

@Composable
private fun PropertySwitch(
    rawKey: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val glass = HorizonGlass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = if (checked) glass.accent.copy(alpha = 0.42f) else glass.hairline
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (rawKey.isNotEmpty()) {
                    KeyBadge(rawKey)
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = glass.accent,
                checkedTrackColor = glass.accentSoft
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabeledNumberField(
    rawKey: String = "",
    label: String,
    desc: String = "",
    value: String,
    presets: List<Pair<String, String>> = emptyList(),
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val glass = HorizonGlass
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = glass.hairline
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (value.isNotBlank()) {
                    Text(
                        value,
                        style = HorizonTabularStyle,
                        color = glass.liveGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                if (rawKey.isNotEmpty()) {
                    KeyBadge(rawKey)
                }
            }
        }
        if (desc.isNotEmpty()) {
            Text(desc, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (presets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { (presetVal, presetLabel) ->
                    SegPill(
                        text = presetLabel,
                        selected = value.trim() == presetVal,
                        compact = true,
                        onClick = { if (enabled) onValueChange(presetVal) }
                    )
                }
            }
        }
        DebouncedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            sanitize = { it.filter { ch -> ch.isDigit() || ch == '-' } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun renderProperty(
    spec: PropertySpec,
    props: Map<String, String>,
    enabled: Boolean = true,
    onChange: (String) -> Unit
) {
    val guide = VI_PROPERTY_GUIDES[spec.key]
    val fallbackLabel = if (spec.labelRes != 0) stringResource(spec.labelRes) else "PVP"
    val fallbackDesc = if (spec.descRes != 0) stringResource(spec.descRes) else ""
    val label = guide?.titleVi ?: fallbackLabel
    val desc = guide?.descVi ?: fallbackDesc

    when (spec.type) {
        PropType.Bool -> PropertySwitch(
            rawKey = spec.key,
            title = label,
            subtitle = desc,
            checked = boolOf(props, spec.key, spec.default.toBoolean()),
            onChange = { if (enabled) onChange(it.toString()) },
            enabled = enabled
        )
        PropType.Int -> LabeledNumberField(
            rawKey = spec.key,
            label = label,
            desc = desc,
            value = props[spec.key] ?: spec.default,
            presets = guide?.intPresets.orEmpty(),
            onValueChange = { if (enabled) onChange(it) },
            enabled = enabled
        )
        PropType.Enum -> EnumField(
            rawKey = spec.key,
            label = label,
            desc = desc,
            value = props[spec.key] ?: spec.default,
            options = spec.options,
            onValueChange = { if (enabled) onChange(it) },
            enabled = enabled
        )
        PropType.Text -> LabeledTextField(
            rawKey = spec.key,
            label = label,
            desc = desc,
            value = props[spec.key] ?: spec.default,
            onValueChange = { if (enabled) onChange(it) },
            enabled = enabled
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnumField(
    rawKey: String = "",
    label: String,
    desc: String,
    value: String,
    options: List<Pair<Int, String>>,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val glass = HorizonGlass
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = glass.hairline
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            )
            if (rawKey.isNotEmpty()) {
                KeyBadge(rawKey)
            }
        }
        if (desc.isNotEmpty()) {
            Text(desc, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (displayRes, v) ->
                SegPill(
                    text = stringResource(displayRes),
                    selected = value == v,
                    onClick = { if (enabled) onValueChange(v) }
                )
            }
        }
    }
}

@Composable
private fun LabeledTextField(
    rawKey: String = "",
    label: String,
    desc: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val glass = HorizonGlass
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = glass.hairline
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            )
            if (rawKey.isNotEmpty()) {
                KeyBadge(rawKey)
            }
        }
        if (desc.isNotEmpty()) {
            Text(desc, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        DebouncedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private enum class PnxFieldType { Toggle, Number, Text, Choice }

private data class PnxField(
    val key: String,
    val labelRes: Int,
    val type: PnxFieldType,
    val default: String,
    val options: List<String> = emptyList()
)

private val pnxGroups = listOf(
    R.string.grp_server to listOf(
        PnxField("server-ip", R.string.pnx_listen_addr, PnxFieldType.Text, "0.0.0.0"),
        PnxField("server-port", R.string.pnx_udp_port, PnxFieldType.Number, "19132"),
        PnxField("max-players", R.string.pnx_max_players, PnxFieldType.Number, "20"),
        PnxField("motd", R.string.pnx_motd, PnxFieldType.Text, "MineHost VN - May chu Bedrock"),
        PnxField("sub-motd", R.string.pnx_sub_motd, PnxFieldType.Text, "May chu Minecraft tren dien thoai"),
        PnxField("language", R.string.pnx_language, PnxFieldType.Text, "eng"),
        PnxField("level-name", R.string.pnx_level_name, PnxFieldType.Text, "world"),
        PnxField("auto-save", R.string.pnx_auto_save, PnxFieldType.Toggle, "true"),
        PnxField("white-list", R.string.pnx_white_list, PnxFieldType.Toggle, "false"),
        PnxField("online-mode", R.string.pnx_online_mode, PnxFieldType.Toggle, "true")
    ),
    R.string.grp_gamemode to listOf(
        PnxField("gamemode", R.string.pnx_gamemode, PnxFieldType.Choice, "survival", listOf("survival", "creative", "adventure", "spectator")),
        PnxField("force-gamemode", R.string.pnx_force_gamemode, PnxFieldType.Toggle, "false"),
        PnxField("difficulty", R.string.pnx_difficulty, PnxFieldType.Choice, "easy", listOf("peaceful", "easy", "normal", "hard")),
        PnxField("pvp", R.string.pnx_pvp, PnxFieldType.Toggle, "true"),
        PnxField("view-distance", R.string.pnx_view_distance, PnxFieldType.Number, JavaServerPerformance.VIEW_DISTANCE.toString()),
        PnxField("spawn-protection", R.string.pnx_spawn_protection, PnxFieldType.Number, "16"),
        PnxField("allow-nether", R.string.pnx_allow_nether, PnxFieldType.Toggle, "true"),
        PnxField("allow-the-end", R.string.pnx_allow_end, PnxFieldType.Toggle, "true"),
        PnxField("enable-command-block", R.string.pnx_cmd_block, PnxFieldType.Toggle, "false"),
        PnxField("spawn-animals", R.string.pnx_spawn_animals, PnxFieldType.Toggle, "true"),
        PnxField("weather-cycle", R.string.pnx_weather, PnxFieldType.Toggle, "true"),
        PnxField("daylight-cycle", R.string.pnx_daylight, PnxFieldType.Toggle, "true"),
        PnxField("hunger", R.string.pnx_hunger, PnxFieldType.Toggle, "true")
    ),
    R.string.grp_perf to listOf(PnxField("base-tps", R.string.pnx_base_tps, PnxFieldType.Number, "20")),
    R.string.grp_net to listOf(PnxField("enable-status", R.string.pnx_enable_status, PnxFieldType.Toggle, "true"))
)

@Composable
fun PropertiesScreen(vm: McViewModel, onBack: () -> Unit, showBackBar: Boolean = true) {
    val config by vm.config.collectAsState()
    if (config.installedCores.isEmpty()) {
        PropertiesEmptyScreen(onBack, showBackBar)
        return
    }
    when (config.installedCores.firstOrNull { it.name == config.activeCoreName }?.core) {
        com.mineserve.mobile.data.ServerCore.PowerNukkitX -> PowerNukkitXPropertiesScreen(vm, onBack, showBackBar)
        null -> PropertiesEmptyScreen(onBack, showBackBar)
        else -> JavaPropertiesScreen(vm, onBack, showBackBar)
    }
}

@Composable
private fun PropertiesEmptyScreen(onBack: () -> Unit, showBackBar: Boolean) {
    val glass = HorizonGlass
    val chromePad = LocalHorizonChromePadding.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (showBackBar) BackBar(title = stringResource(R.string.props_back_title), onBack = onBack)
        HeaderBlock(stringResource(R.string.props_eyebrow), stringResource(R.string.props_back_title), statusBarPadding = !showBackBar)
        Spacer(Modifier.height(8.dp))
        McCard(title = "Chưa có máy chủ — Hãy tạo một Server trước") {
            Text(
                "Hiện chưa có máy chủ nào được cài đặt hoặc chọn để cấu hình server.properties.",
                color = Muted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )
            if (showBackBar) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onBack,
                    border = BorderStroke(1.dp, glass.accent),
                    shape = McShape.Control,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                ) {
                    Text("Quay lại để tạo Server mới", color = glass.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(chromePad.calculateBottomPadding() + 28.dp))
    }
}

@Composable
private fun PropertiesNotGeneratedCard(
    isRunning: Boolean,
    onStartServer: () -> Unit,
    onRefresh: () -> Unit,
    onCreateDefaultNow: (() -> Unit)? = null,
    desc: String = stringResource(R.string.props_not_generated_desc),
    hint: String = stringResource(R.string.props_not_generated_hint)
) {
    val glass = HorizonGlass
    McCard(title = stringResource(R.string.props_not_generated_title)) {
        Text(
            desc,
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            color = Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(12.dp))
        if (onCreateDefaultNow != null && !isRunning) {
            Button(
                onClick = onCreateDefaultNow,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = McShape.Control,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
            ) {
                Text(
                    "⚡ Tạo ngay tệp cấu hình (Chỉnh Tầm nhìn & Cracked trước khi chạy)",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (isRunning) {
            Text(
                stringResource(R.string.props_not_generated_running),
                color = glass.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = McShape.Control,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
            ) {
                Text(
                    stringResource(R.string.props_not_generated_refresh),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            OutlinedButton(
                onClick = onStartServer,
                border = BorderStroke(1.dp, glass.accent),
                shape = McShape.Control,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
            ) {
                Text(
                    stringResource(R.string.props_not_generated_start),
                    color = glass.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.props_not_generated_refresh),
                    color = glass.accent,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PowerNukkitXPropertiesScreen(vm: McViewModel, onBack: () -> Unit, showBackBar: Boolean = true) {
    val glass = HorizonGlass
    val chromePad = LocalHorizonChromePadding.current
    val sharedChrome = LocalMineHostChrome.current
    val loaded by vm.serverProperties.collectAsState()
    val propertiesExist by vm.serverPropertiesExist.collectAsState()
    val state by vm.serverState.collectAsState()
    var props by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var restartConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadServerProperties() }
    LaunchedEffect(loaded) { props = loaded }

    if (propertiesExist == false) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (showBackBar) BackBar(title = stringResource(R.string.props_pnx_back), onBack = onBack)
            HeaderBlock("POWERNUKKITX", stringResource(R.string.props_pnx_title), statusBarPadding = !showBackBar)
            Spacer(Modifier.height(12.dp))
            PropertiesNotGeneratedCard(
                isRunning = state.isRunning,
                onStartServer = { vm.startServer() },
                onRefresh = { vm.loadServerProperties() },
                desc = stringResource(R.string.props_pnx_not_generated_desc),
                hint = stringResource(R.string.props_pnx_not_generated_hint)
            )
            Spacer(Modifier.height(chromePad.calculateBottomPadding() + 28.dp))
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (showBackBar) BackBar(title = stringResource(R.string.props_pnx_back), onBack = onBack)
            HeaderBlock("POWERNUKKITX", stringResource(R.string.props_pnx_title), statusBarPadding = !showBackBar)
            Text(
                stringResource(R.string.props_pnx_source),
                color = glass.warnAmber,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = McSpacing.Page)
            )
            ViewDistanceBoosterCard(
                props = props,
                showSimulationDistance = false,
                onUpdateProps = { props = it },
                onApplyImmediately = { viewDist, _ ->
                    vm.applyQuickServerConfig(viewDistance = viewDist)
                }
            )
            pnxGroups.forEach { (titleRes, fields) ->
                McCard(title = stringResource(titleRes), compact = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fields.forEach { field ->
                            PnxFieldRow(field, props[field.key] ?: field.default) { value ->
                                props = props.toMutableMap().apply { put(field.key, value) }
                            }
                        }
                    }
                }
            }
            McCard(title = stringResource(R.string.props_advanced), compact = true) {
                Text(stringResource(R.string.props_advanced_hint), color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(chromePad.calculateBottomPadding() + 92.dp))
        }
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val saveBottom = if (sharedChrome) {
            maxOf(chromePad.calculateBottomPadding(), imeBottom) + 10.dp
        } else {
            maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), imeBottom) + 10.dp
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = McSpacing.Page, end = McSpacing.Page, top = 6.dp, bottom = saveBottom)
                .horizonGlassPlate(
                    shape = RoundedCornerShape(22.dp),
                    containerColor = glass.glassChrome,
                    borderColor = glass.specularTop.copy(alpha = 0.42f)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Button(
                onClick = { if (state.isRunning) restartConfirm = true else vm.saveServerProperties(props) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = McShape.Control,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    "💾 " + stringResource(R.string.props_save),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
    if (restartConfirm) AlertDialog(
        onDismissRequest = { restartConfirm = false },
        containerColor = glass.glassChrome,
        shape = McShape.Dialog,
        tonalElevation = 0.dp,
        title = {
            ApplyHorizonDialogBlur()
            Text(stringResource(R.string.props_save), fontWeight = FontWeight.Bold)
        },
        text = { Text(stringResource(R.string.props_save_running)) },
        confirmButton = {
            TextButton(onClick = { restartConfirm = false; vm.saveServerProperties(props) }) {
                Text(stringResource(R.string.props_save), color = glass.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { restartConfirm = false }) {
                Text(stringResource(R.string.props_cancel), color = glass.dangerCoral)
            }
        }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PnxFieldRow(field: PnxField, value: String, onChange: (String) -> Unit) {
    val guide = VI_PROPERTY_GUIDES[field.key]
    val title = guide?.titleVi ?: stringResource(field.labelRes)
    val desc = guide?.descVi.orEmpty()
    when (field.type) {
        PnxFieldType.Toggle -> PropertySwitch(
            rawKey = field.key,
            title = title,
            subtitle = desc,
            checked = value.equals("true", true),
            onChange = { onChange(it.toString()) }
        )
        PnxFieldType.Number -> LabeledNumberField(
            rawKey = field.key,
            label = title,
            desc = desc,
            value = value,
            presets = guide?.intPresets.orEmpty(),
            onValueChange = onChange
        )
        PnxFieldType.Text -> LabeledTextField(
            rawKey = field.key,
            label = title,
            desc = desc,
            value = value,
            onValueChange = onChange
        )
        PnxFieldType.Choice -> {
            val glass = HorizonGlass
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(
                        shape = McShape.Row,
                        containerColor = glass.glassRow,
                        borderColor = glass.hairline
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    KeyBadge(field.key)
                }
                if (desc.isNotEmpty()) {
                    Text(desc, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    field.options.forEach { option ->
                        SegPill(option, value == option) { onChange(option) }
                    }
                }
            }
        }
    }
}
