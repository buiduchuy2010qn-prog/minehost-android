package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.server.PluginManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mod/Plugin cần cài ở đâu: chỉ Server, chỉ Client (máy người chơi) hay cả 2.
 */
internal enum class AddonSide(val label: String, val explain: String, val color: Color) {
    SERVER_ONLY(
        "🖥️ Chỉ cần Server",
        "Chỉ cài trên server. Người chơi KHÔNG cần cài gì vẫn vào được (kể cả PE).",
        Color(0xFF10B981)
    ),
    SERVER_CLIENT_OPTIONAL(
        "🖥️ Server (Client tùy chọn)",
        "Cài trên server là chạy. Người chơi cài thêm thì có thêm tính năng/hiển thị, không bắt buộc.",
        Color(0xFF14B8A6)
    ),
    BOTH_REQUIRED(
        "🔁 Cả Server + Client",
        "Server và máy người chơi đều PHẢI cài mod này (cùng phiên bản), thiếu sẽ không vào được. Người chơi PE không cài được mod.",
        Color(0xFFF59E0B)
    ),
    CLIENT_SERVER_OPTIONAL(
        "📱 Client (Server tùy chọn)",
        "Chủ yếu chạy ở máy người chơi. Cài trên server chỉ để hỗ trợ thêm, không bắt buộc.",
        Color(0xFF6366F1)
    ),
    CLIENT_ONLY(
        "📱 Chỉ Client",
        "Mod cho máy người chơi (đồ họa, giao diện, FPS…). Cài lên server không có tác dụng, có thể gây lỗi → nên tắt trên server.",
        Color(0xFF8B5CF6)
    ),
    EITHER(
        "🔀 Server hoặc Client",
        "Cài ở bên nào thì bên đó được lợi, không bắt buộc cả hai.",
        Color(0xFF0EA5E9)
    ),
    LIBRARY(
        "📚 Thư viện",
        "Thư viện cho mod khác: cài ở bên nào có mod cần nó (server, máy chơi hoặc cả hai).",
        Color(0xFF64748B)
    ),
    PLUGIN(
        "🖥️ Plugin – chỉ Server",
        "Plugin chạy trên server. Người chơi (PC lẫn PE) không cần cài gì.",
        Color(0xFF10B981)
    ),
    UNKNOWN(
        "❔ Chưa rõ Server/Client",
        "Chưa xác định được. Mod thêm khối/vật phẩm/sinh vật mới thường phải cài cả 2 bên.",
        Color(0xFF94A3B8)
    );

    companion object {
        fun fromModrinth(client: String?, server: String?): AddonSide? {
            val c = client?.lowercase(Locale.ROOT).orEmpty()
            val s = server?.lowercase(Locale.ROOT).orEmpty()
            if (c.isBlank() && s.isBlank()) return null
            return when {
                s == "unsupported" && c != "unsupported" -> CLIENT_ONLY
                c == "unsupported" && s != "unsupported" -> SERVER_ONLY
                c == "required" && s == "required" -> BOTH_REQUIRED
                s == "required" && c == "optional" -> SERVER_CLIENT_OPTIONAL
                c == "required" && s == "optional" -> CLIENT_SERVER_OPTIONAL
                c == "optional" && s == "optional" -> EITHER
                else -> null
            }
        }

        fun fromFabricEnvironment(env: String?): AddonSide? = when (env?.trim()?.lowercase(Locale.ROOT)) {
            "server" -> SERVER_ONLY
            "client" -> CLIENT_ONLY
            else -> null
        }
    }
}

internal data class AddonInfo(
    val side: AddonSide,
    val descVi: String?,
    val autoTranslated: Boolean = false,
    val originalDesc: String? = null,
    val categoriesVi: String? = null
)

/**
 * Kho kiến thức Mod/Plugin bằng tiếng Việt + tra cứu Modrinth (Server/Client) + dịch tự động mô tả.
 */
internal object AddonInfoVi {
    private data class K(val side: AddonSide?, val desc: String)

    private val S = AddonSide.SERVER_ONLY
    private val SO = AddonSide.SERVER_CLIENT_OPTIONAL
    private val B = AddonSide.BOTH_REQUIRED
    private val CO = AddonSide.CLIENT_SERVER_OPTIONAL
    private val C = AddonSide.CLIENT_ONLY
    private val E = AddonSide.EITHER
    private val L = AddonSide.LIBRARY
    private val P = AddonSide.PLUGIN

    private val curated: Map<String, K> = mapOf(
        // ── Tối ưu server ──
        "lithium" to K(S, "Tối ưu logic server (AI quái, vật lý, phễu, redstone) giúp tăng TPS mà vẫn giữ nguyên cơ chế Vanilla."),
        "radium" to K(S, "Bản Lithium cho Forge/NeoForge: tối ưu logic server, tăng TPS."),
        "canary" to K(S, "Bản Lithium cho Forge: tối ưu logic server, tăng TPS."),
        "ferritecore" to K(E, "Giảm mạnh RAM bằng cách nén dữ liệu khối/model. Cài ở server thì server đỡ tốn RAM; cài ở máy chơi thì máy chơi đỡ tốn RAM."),
        "ferrite-core" to K(E, "Giảm mạnh RAM bằng cách nén dữ liệu khối/model. Cài ở server thì server đỡ tốn RAM; cài ở máy chơi thì máy chơi đỡ tốn RAM."),
        "c2me" to K(S, "Tạo và tải chunk (vùng đất) song song bằng nhiều nhân CPU, giảm giật khi người chơi chạy/bay nhanh."),
        "c2me-fabric" to K(S, "Tạo và tải chunk (vùng đất) song song bằng nhiều nhân CPU, giảm giật khi người chơi chạy/bay nhanh."),
        "c2me-neoforge" to K(S, "Tạo và tải chunk (vùng đất) song song bằng nhiều nhân CPU, giảm giật khi người chơi chạy/bay nhanh."),
        "noisium" to K(S, "Tăng tốc tạo địa hình mới khi người chơi đi khám phá (sinh map nhanh hơn)."),
        "noisiumforked" to K(S, "Tăng tốc tạo địa hình mới khi người chơi đi khám phá (sinh map nhanh hơn)."),
        "krypton" to K(S, "Tối ưu tầng mạng: giảm CPU và băng thông khi gửi dữ liệu cho người chơi, đỡ lag khi chơi qua 4G/Playit."),
        "pluto" to K(S, "Bản Krypton cho Forge: tối ưu tầng mạng, giảm CPU và băng thông."),
        "modernfix" to K(E, "Sửa rò rỉ bộ nhớ, giảm RAM và giúp server/game khởi động nhanh hơn."),
        "modernfix-mvus" to K(E, "Sửa rò rỉ bộ nhớ, giảm RAM và giúp server/game khởi động nhanh hơn."),
        "servercore" to K(S, "Tự giảm tải (tầm nhìn, sinh quái, AI) khi server quá tải để giữ TPS ổn định."),
        "vmp" to K(S, "Very Many Players: tối ưu khi đông người chơi (theo dõi thực thể, gửi chunk)."),
        "vmp-fabric" to K(S, "Very Many Players: tối ưu khi đông người chơi (theo dõi thực thể, gửi chunk)."),
        "alternate-current" to K(S, "Viết lại cách tính dây redstone nhanh hơn rất nhiều, giảm lag ở các máy redstone."),
        "clumps" to K(SO, "Gộp các viên kinh nghiệm (XP) nằm gần nhau thành một, giảm lag khi farm quái."),
        "letmedespawn" to K(S, "Cho quái đã nhặt đồ vẫn tự biến mất như bình thường, tránh tích tụ quái gây lag."),
        "spark" to K(SO, "Đo hiệu năng: xem TPS, CPU, RAM và tìm chính xác mod/khu vực gây lag (lệnh /spark)."),
        "chunky" to K(S, "Tạo sẵn map trước (pre-generate) để lúc chơi không bị giật khi đi xa. Dùng lệnh /chunky."),
        "threadtweak" to K(S, "Điều chỉnh ưu tiên luồng xử lý để server chạy mượt hơn."),
        "neruina" to K(S, "Chống crash: khi một thực thể/khối bị lỗi lúc tick, mod bỏ qua hoặc xóa nó thay vì làm sập server."),
        "ksyxis" to K(S, "Vào thế giới nhanh hơn nhờ không nạp sẵn vùng spawn lúc khởi động."),
        "ledger" to K(S, "Ghi lại lịch sử phá/đặt khối, mở rương… để tra ai phá và khôi phục (giống CoreProtect)."),
        "fastload" to K(E, "Giảm thời gian chờ khi vào thế giới."),
        "starlight" to K(E, "Viết lại hệ thống ánh sáng, giảm lag khi tạo và tải chunk."),
        "debugify" to K(E, "Sửa hàng loạt lỗi (bug) có sẵn của Minecraft gốc."),
        "carpet" to K(E, "Công cụ cho dân kỹ thuật/redstone: đo lag, chỉnh luật game, người chơi giả (/player)."),
        "servux" to K(S, "Gửi thông tin server cho các mod MiniHUD, Litematica ở máy người chơi."),
        "fabric-carpet" to K(E, "Công cụ cho dân kỹ thuật/redstone: đo lag, chỉnh luật game, người chơi giả (/player)."),
        // ── Chỉ máy người chơi ──
        "sodium" to K(C, "Tăng FPS rất mạnh nhờ engine vẽ mới. Chỉ cài ở máy người chơi."),
        "embeddium" to K(C, "Bản Sodium cho Forge/NeoForge: tăng FPS. Chỉ cài ở máy người chơi."),
        "iris" to K(C, "Cho phép dùng shader (bóng đổ, ánh sáng đẹp) trên Fabric/NeoForge. Chỉ cài ở máy người chơi."),
        "oculus" to K(C, "Shader cho Forge. Chỉ cài ở máy người chơi."),
        "indium" to K(C, "Phụ trợ cho Sodium để các mod khác hiển thị đúng. Chỉ cài ở máy người chơi."),
        "immediatelyfast" to K(C, "Tăng FPS khi vẽ chữ, bản đồ, HUD. Chỉ cài ở máy người chơi."),
        "entityculling" to K(C, "Không vẽ thực thể/khối bị che khuất để tăng FPS. Chỉ cài ở máy người chơi."),
        "dynamic-fps" to K(C, "Giảm FPS khi game chạy nền để tiết kiệm pin/CPU. Chỉ cài ở máy người chơi."),
        "moreculling" to K(C, "Bỏ vẽ các mặt khối không nhìn thấy, tăng FPS. Chỉ cài ở máy người chơi."),
        "badoptimizations" to K(C, "Nhiều tối ưu nhỏ giúp tăng FPS. Chỉ cài ở máy người chơi."),
        "lambdynamiclights" to K(C, "Cầm đuốc/đèn trên tay là xung quanh sáng lên. Chỉ cài ở máy người chơi."),
        "continuity" to K(C, "Kính và khối liền mạch (connected textures). Chỉ cài ở máy người chơi."),
        "3dskinlayers" to K(C, "Làm lớp ngoài skin nổi 3D. Chỉ cài ở máy người chơi."),
        "skin-layers-3d" to K(C, "Làm lớp ngoài skin nổi 3D. Chỉ cài ở máy người chơi."),
        "modmenu" to K(C, "Thêm nút \"Mods\" trong menu để xem và chỉnh cấu hình các mod. Chỉ cài ở máy người chơi."),
        "zoomify" to K(C, "Phóng to (zoom) như ống nhòm. Chỉ cài ở máy người chơi."),
        "malilib" to K(C, "Thư viện cho MiniHUD, Litematica, Tweakeroo (các mod phía máy người chơi)."),
        "litematica" to K(C, "Hiện bản thiết kế công trình (schematic) để xây theo. Chỉ cài ở máy người chơi."),
        "minihud" to K(C, "Hiện tọa độ, FPS, vùng spawn quái… trên màn hình. Chỉ cài ở máy người chơi."),
        "tweakeroo" to K(C, "Nhiều tiện ích điều khiển cho người chơi. Chỉ cài ở máy người chơi."),
        "essential" to K(C, "Tiện ích cho máy người chơi (bạn bè, ảnh chụp, mỹ phẩm). Không cần cho server."),
        "cit-resewn" to K(C, "Hỗ trợ texture riêng theo tên vật phẩm (OptiFine CIT). Chỉ cài ở máy người chơi."),
        "entity-model-features" to K(C, "Hỗ trợ model thực thể tùy chỉnh từ resource pack. Chỉ cài ở máy người chơi."),
        "entitytexturefeatures" to K(C, "Hỗ trợ texture thực thể nâng cao từ resource pack. Chỉ cài ở máy người chơi."),
        // ── Tiện ích hiển thị (client, server tùy chọn) ──
        "jei" to K(CO, "Xem công thức chế tạo của mọi vật phẩm (Just Enough Items)."),
        "roughly-enough-items" to K(CO, "Xem công thức chế tạo của mọi vật phẩm (REI)."),
        "rei" to K(CO, "Xem công thức chế tạo của mọi vật phẩm (REI)."),
        "emi" to K(CO, "Xem công thức chế tạo của mọi vật phẩm (EMI)."),
        "jade" to K(CO, "Hiện tên và thông tin khối/sinh vật đang nhìn vào (máu, nội dung rương…)."),
        "wthit" to K(CO, "Hiện tên và thông tin khối/sinh vật đang nhìn vào."),
        "xaeros-minimap" to K(CO, "Bản đồ nhỏ ở góc màn hình, đánh dấu điểm (waypoint)."),
        "xaerominimap" to K(CO, "Bản đồ nhỏ ở góc màn hình, đánh dấu điểm (waypoint)."),
        "xaeros-world-map" to K(CO, "Bản đồ thế giới toàn màn hình (phím M)."),
        "xaeroworldmap" to K(CO, "Bản đồ thế giới toàn màn hình (phím M)."),
        "journeymap" to K(CO, "Bản đồ nhỏ + bản đồ toàn màn hình, waypoint."),
        "appleskin" to K(E, "Hiện độ no và độ bão hòa của thức ăn. Máy người chơi cài để xem; server cài để số liệu chính xác."),
        "emotecraft" to K(CO, "Nhảy múa, biểu cảm nhân vật. Muốn người khác thấy thì server cũng nên cài."),
        "distanthorizons" to K(CO, "Hiển thị địa hình rất xa (LOD). Nặng máy, chủ yếu cho máy người chơi."),
        "mouse-tweaks" to K(C, "Kéo thả đồ trong túi nhanh bằng chuột. Chỉ cài ở máy người chơi."),
        "inventory-profiles-next" to K(CO, "Tự sắp xếp túi đồ, rương."),
        // ── Thư viện ──
        "fabric-api" to K(L, "Thư viện nền tảng của Fabric; hầu hết mod Fabric cần nó. Server Fabric gần như luôn phải có."),
        "fabric-language-kotlin" to K(L, "Thư viện cho các mod viết bằng ngôn ngữ Kotlin."),
        "qsl" to K(L, "Thư viện nền tảng của Quilt."),
        "architectury-api" to K(L, "Thư viện giúp mod chạy được cả Fabric lẫn Forge; nhiều mod lớn cần nó."),
        "architectury" to K(L, "Thư viện giúp mod chạy được cả Fabric lẫn Forge; nhiều mod lớn cần nó."),
        "cloth-config" to K(L, "Thư viện màn hình cấu hình cho mod."),
        "balm" to K(L, "Thư viện chung của BlayTheNinth (Waystones, Cooking for Blockheads…)."),
        "puzzles-lib" to K(L, "Thư viện của Fuzs, nhiều mod tiện ích cần."),
        "puzzleslib" to K(L, "Thư viện của Fuzs, nhiều mod tiện ích cần."),
        "forge-config-api-port" to K(L, "Cho mod Fabric dùng kiểu cấu hình của Forge."),
        "forgeconfigapiport" to K(L, "Cho mod Fabric dùng kiểu cấu hình của Forge."),
        "collective" to K(L, "Thư viện chung cho các mod của Serilum."),
        "cristel-lib" to K(L, "Thư viện cho các mod tạo cấu trúc (công trình) của Cristelknight."),
        "cristellib" to K(L, "Thư viện cho các mod tạo cấu trúc (công trình) của Cristelknight."),
        "craterlib" to K(L, "Thư viện chung cho các mod của HypherionSA."),
        "geckolib" to K(L, "Thư viện hoạt ảnh 3D cho sinh vật/vật phẩm; mod dùng nó thường cần cài cả 2 bên."),
        "yacl" to K(L, "YetAnotherConfigLib: thư viện màn hình cấu hình cho mod."),
        "yet-another-config-lib" to K(L, "YetAnotherConfigLib: thư viện màn hình cấu hình cho mod."),
        "yet-another-config-lib-v3" to K(L, "YetAnotherConfigLib: thư viện màn hình cấu hình cho mod."),
        "placeholder-api" to K(L, "Thư viện biến hiển thị (%player%…) cho các mod Fabric phía server."),
        "cardinal-components-api" to K(L, "Thư viện lưu dữ liệu thêm cho thực thể/thế giới."),
        "owo-lib" to K(L, "Thư viện giao diện và tiện ích cho mod."),
        "kotlin-for-forge" to K(L, "Thư viện Kotlin cho Forge/NeoForge."),
        "kotlinforforge" to K(L, "Thư viện Kotlin cho Forge/NeoForge."),
        "moonlight" to K(L, "Thư viện của MehVahdJukaar (Supplementaries…)."),
        "resourcefullib" to K(L, "Thư viện chung của Team Resourceful."),
        "bookshelf" to K(L, "Thư viện chung của Darkhax."),
        "prism-lib" to K(L, "Thư viện màu sắc/giao diện cho mod."),
        "midnightlib" to K(L, "Thư viện cấu hình nhỏ gọn cho mod."),
        "terrablender" to K(B, "Thư viện thêm quần xã (biome) mới; mod tạo thế giới dùng nó cần cả 2 bên."),
        "lithostitched" to K(L, "Thư viện hỗ trợ mod tạo thế giới tương thích nhau."),
        "supermartijn642corelib" to K(L, "Thư viện chung của SuperMartijn642."),
        "supermartijn642configlib" to K(L, "Thư viện cấu hình của SuperMartijn642."),
        "fzzy-config" to K(L, "Thư viện cấu hình cho mod."),
        "polymer" to K(S, "Cho phép mod server-side thêm khối/vật phẩm mà người chơi không cần cài mod."),
        // ── Cầu nối Java/PE & phiên bản ──
        "geyser" to K(S, "Cầu nối cho Minecraft PE/Bedrock (điện thoại, console, Windows) vào server Java."),
        "geyser-spigot" to K(P, "Cầu nối cho Minecraft PE/Bedrock (điện thoại, console, Windows) vào server Java."),
        "geyser-fabric" to K(S, "Cầu nối cho Minecraft PE/Bedrock vào server Fabric."),
        "geyser-neoforge" to K(S, "Cầu nối cho Minecraft PE/Bedrock vào server NeoForge."),
        "floodgate" to K(S, "Cho người chơi PE vào bằng tài khoản Xbox, không cần tài khoản Java."),
        "floodgate-spigot" to K(P, "Cho người chơi PE vào bằng tài khoản Xbox, không cần tài khoản Java."),
        "floodgate-fabric" to K(S, "Cho người chơi PE vào bằng tài khoản Xbox, không cần tài khoản Java."),
        "viaversion" to K(S, "Cho máy Java bản MỚI hơn server vẫn vào được (Geyser cũng cần nó khi server không phải bản mới nhất)."),
        "viabackwards" to K(S, "Cho máy Java bản CŨ hơn server vẫn vào được (cần ViaVersion)."),
        "viarewind" to K(S, "Cho máy Java rất cũ (1.7–1.8) vào server mới (cần ViaVersion + ViaBackwards)."),
        "viafabric" to K(S, "ViaVersion cho Fabric: người chơi khác phiên bản vẫn vào được."),
        "viafabricplus" to K(C, "Cho máy người chơi Fabric vào server khác phiên bản. Chỉ cài ở máy người chơi."),
        // ── Nội dung (thường cần cả 2 bên) ──
        "create" to K(B, "Thêm máy móc cơ khí: bánh răng, băng chuyền, máy nghiền… Cả server và người chơi phải cài."),
        "farmers-delight" to K(B, "Thêm nấu ăn, cây trồng, dao, bếp và hàng chục món ăn mới."),
        "farmersdelight" to K(B, "Thêm nấu ăn, cây trồng, dao, bếp và hàng chục món ăn mới."),
        "waystones" to K(B, "Đá dịch chuyển: đặt đá ở nhiều nơi rồi dịch chuyển qua lại."),
        "simple-voice-chat" to K(B, "Chat giọng nói theo khoảng cách trong game. Server và người chơi đều phải cài (PE không dùng được). Cần mở thêm cổng UDP 24454."),
        "voicechat" to K(B, "Chat giọng nói theo khoảng cách trong game. Server và người chơi đều phải cài (PE không dùng được). Cần mở thêm cổng UDP 24454."),
        "travelersbackpack" to K(B, "Thêm ba lô đeo lưng chứa đồ, có bình nước."),
        "sophisticated-backpacks" to K(B, "Thêm ba lô nâng cấp được (tự nhặt đồ, ăn tự động…)."),
        "macaws-furniture" to K(B, "Thêm bàn ghế, tủ, giường… nội thất trang trí."),
        "macaws-bridges" to K(B, "Thêm nhiều kiểu cầu trang trí."),
        "macaws-doors" to K(B, "Thêm nhiều kiểu cửa ra vào."),
        "macaws-windows" to K(B, "Thêm nhiều kiểu cửa sổ."),
        "macaws-roofs" to K(B, "Thêm nhiều kiểu mái nhà."),
        "macaws-trapdoors" to K(B, "Thêm nhiều kiểu cửa sập."),
        "macaws-fences-and-walls" to K(B, "Thêm nhiều kiểu hàng rào và tường."),
        "macaws-lights-and-lamps" to K(B, "Thêm nhiều kiểu đèn trang trí."),
        "macaws-paths-and-pavings" to K(B, "Thêm lối đi, gạch lát đường."),
        "macaws-stairs" to K(B, "Thêm nhiều kiểu cầu thang."),
        "macaws-holidays" to K(B, "Thêm đồ trang trí lễ hội (Giáng sinh, Halloween…)."),
        "supplementaries" to K(B, "Thêm hàng trăm đồ trang trí và tiện ích nhỏ (lọ, biển, chuông…)."),
        "terralith" to K(S, "Thêm hơn 95 quần xã (biome) đẹp mới. Chỉ cần cài trên server, người chơi không cần cài."),
        "tectonic" to K(S, "Địa hình núi non, hang động hoành tráng hơn. Chỉ cần cài trên server."),
        "incendium" to K(S, "Làm mới địa ngục (Nether) với quần xã và công trình mới. Chỉ cần cài trên server."),
        "nullscape" to K(S, "Làm mới vùng End. Chỉ cần cài trên server."),
        "dungeons-and-taverns" to K(S, "Thêm hầm ngục, quán trọ, công trình mới khi khám phá. Chỉ cần cài trên server."),
        "towns-and-towers" to K(S, "Thêm làng và tháp mới. Chỉ cần cài trên server."),
        "yungs-better-dungeons" to K(S, "Làm lại hầm ngục đẹp và thử thách hơn. Chỉ cần cài trên server."),
        "yungs-better-mineshafts" to K(S, "Làm lại hầm mỏ bỏ hoang đẹp hơn. Chỉ cần cài trên server."),
        "yungs-api" to K(L, "Thư viện cho các mod YUNG's."),
        "universal-graves" to K(S, "Khi chết, đồ được cất vào mộ thay vì rơi ra. Chỉ cần cài trên server."),
        "gravestones" to K(B, "Khi chết, đồ được cất vào mộ thay vì rơi ra."),
        "styled-chat" to K(S, "Tùy biến khung chat, màu chữ phía server."),
        "worldedit" to K(SO, "Công cụ xây/chỉnh map hàng loạt (//set, //copy, //paste…). Chỉ cần ở server."),
        "luckperms" to K(S, "Quản lý phân quyền: rank, quyền dùng lệnh cho từng người chơi."),
        // ── Plugin phổ biến ──
        "essentialsx" to K(P, "Bộ lệnh cơ bản: /home, /spawn, /tpa, /warp, kit, tiền tệ, nickname…"),
        "essentials" to K(P, "Bộ lệnh cơ bản: /home, /spawn, /tpa, /warp, kit, tiền tệ, nickname…"),
        "essentialsxchat" to K(P, "Định dạng khung chat cho EssentialsX."),
        "essentialsxspawn" to K(P, "Quản lý điểm spawn cho EssentialsX."),
        "vault" to K(P, "Cầu nối tiền tệ/quyền cho các plugin khác — nhiều plugin bắt buộc phải có."),
        "worldguard" to K(P, "Bảo vệ khu vực: cấm phá, cấm PvP, cấm quái… (cần WorldEdit)."),
        "coreprotect" to K(P, "Ghi lịch sử phá/đặt khối, rương… để tra ai phá và khôi phục (/co rollback)."),
        "griefprevention" to K(P, "Người chơi tự khoanh đất bằng xẻng vàng để chống bị phá."),
        "multiverse-core" to K(P, "Tạo và quản lý nhiều thế giới trên cùng một server."),
        "skinsrestorer" to K(P, "Hiện đúng skin cho người chơi bản crack/offline (TLauncher, Pojav…)."),
        "authme" to K(P, "Bắt người chơi /register và /login — rất nên có khi bật chế độ offline/crack."),
        "authmereloaded" to K(P, "Bắt người chơi /register và /login — rất nên có khi bật chế độ offline/crack."),
        "placeholderapi" to K(P, "Thư viện biến hiển thị (%player_name%…) cho các plugin khác."),
        "tab" to K(P, "Tùy biến bảng TAB, tên trên đầu người chơi và tiêu đề."),
        "discordsrv" to K(P, "Kết nối chat server với kênh Discord."),
        "citizens" to K(P, "Tạo NPC (nhân vật ảo) đứng trong map."),
        "mcmmo" to K(P, "Hệ thống kỹ năng kiểu RPG: đào, chặt, chiến đấu… lên cấp."),
        "jobs" to K(P, "Nghề nghiệp: làm việc (đào, chặt, giết quái) để kiếm tiền."),
        "jobs-reborn" to K(P, "Nghề nghiệp: làm việc (đào, chặt, giết quái) để kiếm tiền."),
        "chestshop" to K(P, "Mở cửa hàng bằng cách gắn biển lên rương."),
        "shopkeepers" to K(P, "Tạo NPC bán hàng cho người chơi."),
        "decentholograms" to K(P, "Tạo chữ nổi (hologram) trong map."),
        "protocollib" to K(P, "Thư viện xử lý gói tin, nhiều plugin cần."),
        "packetevents" to K(P, "Thư viện xử lý gói tin, nhiều plugin cần."),
        "deluxemenus" to K(P, "Tạo menu giao diện (GUI) bằng rương."),
        "towny" to K(P, "Hệ thống thị trấn, quốc gia, đất đai."),
        "lands" to K(P, "Khoanh đất, lập vương quốc, chiến tranh."),
        "clearlag" to K(P, "Tự dọn vật phẩm rơi và thực thể thừa để giảm lag."),
        "harbor" to K(P, "Chỉ cần một số người ngủ là qua đêm."),
        "dynmap" to K(P, "Bản đồ thế giới xem trực tuyến trên trình duyệt web."),
        "bluemap" to K(P, "Bản đồ 3D của thế giới xem trên trình duyệt web."),
        "plan" to K(P, "Thống kê người chơi, thời gian online, hiệu năng."),
        "treeassist" to K(P, "Chặt một khúc là đổ cả cây, tự trồng lại cây con."),
        "veinminer" to K(P, "Đào một khối quặng là đào cả mạch quặng."),
        "chunky-bukkit" to K(P, "Tạo sẵn map trước (pre-generate) để lúc chơi không bị giật. Dùng lệnh /chunky."),
        "spark-bukkit" to K(P, "Đo hiệu năng: xem TPS, CPU, RAM và tìm nguyên nhân gây lag (/spark).")
    )

    private val catalog = ConcurrentHashMap<String, K>()

    /** Đăng ký mô tả tiếng Việt có sẵn của danh mục 56 mod (để mod đã cài cũng hiện đúng mô tả). */
    fun registerCatalog(slug: String, originalJarName: String, title: String, scopeVi: String, descVi: String) {
        if (descVi.isBlank()) return
        val lower = scopeVi.lowercase(Locale.ROOT)
        val side = when {
            lower.contains("chỉ cần server") || lower.contains("chi can server") -> AddonSide.SERVER_ONLY
            lower.contains("chỉ cần client") || lower.contains("chỉ client") -> AddonSide.CLIENT_ONLY
            lower.contains("server") && lower.contains("client") -> AddonSide.BOTH_REQUIRED
            else -> null
        }
        val k = K(side, descVi)
        listOf(slug, title, cleanName(originalJarName)).forEach { key ->
            val n = norm(key)
            if (n.isNotBlank() && n !in curated) catalog[n] = k
        }
    }

    private fun norm(raw: String?): String =
        raw.orEmpty().trim().lowercase(Locale.ROOT).replace('_', '-').replace(' ', '-').removePrefix("-")

    /** "lithium-fabric-0.15.0+mc1.21.11.jar" -> "lithium" */
    fun cleanName(fileOrBase: String): String {
        val base = fileOrBase.removeSuffix(".disabled").removeSuffix(".jar").removePrefix("-")
        val tokens = base.lowercase(Locale.ROOT).replace('_', '-').replace(' ', '-').split('-', '+')
        val stopWords = setOf("fabric", "forge", "neoforge", "quilt", "paper", "spigot", "bukkit", "mc", "universal", "all")
        val kept = mutableListOf<String>()
        for (t in tokens) {
            if (t.isEmpty()) continue
            if (t in stopWords) break
            if (kept.isNotEmpty() && (t.first().isDigit() || t.first() == 'v' && t.length > 1 && t[1].isDigit())) break
            if (t.startsWith("mc") && t.length > 2 && t[2].isDigit()) break
            kept += t
        }
        return kept.joinToString("-").ifBlank { base.lowercase(Locale.ROOT) }
    }

    private fun lookupKnown(vararg hints: String?): K? {
        val keys = hints.filterNotNull().filter { it.isNotBlank() }.flatMap { listOf(norm(it), norm(cleanName(it))) }.distinct()
        for (k in keys) {
            curated[k]?.let { return it }
            catalog[k]?.let { return it }
        }
        // Chỉ bỏ hậu tố nền tảng: "geyser-spigot" -> "geyser", "luckperms-bukkit" -> "luckperms" (không đoán bừa theo tiền tố)
        val platformSuffixes = setOf("spigot", "bukkit", "paper", "fabric", "forge", "neoforge", "quilt", "plugin", "mod", "velocity", "bungee")
        for (k in keys) {
            val parts = k.split('-')
            if (parts.size >= 2 && parts.last() in platformSuffixes) {
                val prefix = parts.dropLast(1).joinToString("-")
                curated[prefix]?.let { return it }
                catalog[prefix]?.let { return it }
            }
        }
        return null
    }

    private val categoryVi = mapOf(
        "optimization" to "Tối ưu", "library" to "Thư viện", "adventure" to "Phiêu lưu", "decoration" to "Trang trí",
        "equipment" to "Trang bị", "food" to "Ẩm thực", "magic" to "Phép thuật", "management" to "Quản lý",
        "minigame" to "Minigame", "mobs" to "Sinh vật", "social" to "Cộng đồng", "storage" to "Lưu trữ",
        "technology" to "Công nghệ", "transportation" to "Di chuyển", "utility" to "Tiện ích",
        "worldgen" to "Tạo thế giới", "economy" to "Kinh tế", "game-mechanics" to "Cơ chế game",
        "cursed" to "Vui nhộn", "challenging" to "Thử thách", "combat" to "Chiến đấu", "kitchen-sink" to "Tổng hợp",
        "lightweight" to "Nhẹ", "multiplayer" to "Nhiều người chơi", "quests" to "Nhiệm vụ", "tech" to "Công nghệ"
    )
    private val loaderWords = setOf("fabric", "forge", "neoforge", "quilt", "paper", "spigot", "bukkit", "purpur", "folia", "velocity", "bungeecord", "waterfall", "sponge", "datapack", "liteloader", "modloader", "rift")

    fun categoriesVi(categories: List<String>): String? =
        categories.filter { it !in loaderWords }.mapNotNull { categoryVi[it] }.distinct().take(3).joinToString(" · ").ifBlank { null }

    // ── Thông tin nhanh (không gọi mạng) ──

    fun quickForMod(mod: PluginManager.ModEntry): AddonInfo {
        val known = lookupKnown(mod.modId, mod.baseName, mod.displayName)
        val side = known?.side ?: AddonSide.fromFabricEnvironment(mod.environment) ?: AddonSide.UNKNOWN
        return AddonInfo(side, known?.desc, originalDesc = mod.description)
    }

    fun quickForPlugin(p: PluginManager.InstalledPlugin): AddonInfo {
        val known = lookupKnown(p.meta?.name, p.baseName)
        val side = when (known?.side) {
            AddonSide.BOTH_REQUIRED -> AddonSide.BOTH_REQUIRED
            else -> AddonSide.PLUGIN
        }
        return AddonInfo(side, known?.desc, originalDesc = p.meta?.description?.takeIf { it.isNotBlank() })
    }

    fun quickForHit(hit: PluginManager.ModrinthHit, isPlugin: Boolean): AddonInfo {
        val known = lookupKnown(hit.slug, hit.title)
        val mr = AddonSide.fromModrinth(hit.client_side, hit.server_side)
        var side = known?.side ?: mr ?: if (isPlugin) AddonSide.PLUGIN else AddonSide.UNKNOWN
        if (isPlugin && side == AddonSide.SERVER_ONLY) side = AddonSide.PLUGIN
        return AddonInfo(
            side = side,
            descVi = known?.desc,
            originalDesc = hit.description.takeIf { it.isNotBlank() },
            categoriesVi = categoriesVi(hit.categories)
        )
    }

    // ── Thông tin đầy đủ (có thể gọi mạng: Modrinth + dịch) ──

    suspend fun fullForMod(mod: PluginManager.ModEntry): AddonInfo {
        val quick = quickForMod(mod)
        if (quick.descVi != null && quick.side != AddonSide.UNKNOWN) return quick
        var side = quick.side
        var original = quick.originalDesc
        var cats: String? = null
        val project = modrinthProjectForJar(mod.filePath)
        if (project != null) {
            if (side == AddonSide.UNKNOWN) {
                AddonSide.fromModrinth(project.optString("client_side"), project.optString("server_side"))?.let { side = it }
            }
            if (original.isNullOrBlank()) original = project.optString("description").takeIf { it.isNotBlank() }
            val arr = project.optJSONArray("categories")
            if (arr != null) cats = categoriesVi((0 until arr.length()).map { arr.optString(it) })
        }
        if (quick.descVi != null) return quick.copy(side = side, categoriesVi = cats)
        val vi = original?.let { translateToVi(it) }
        return AddonInfo(side, vi ?: original, autoTranslated = vi != null, originalDesc = original, categoriesVi = cats)
    }

    suspend fun fullForPlugin(p: PluginManager.InstalledPlugin): AddonInfo {
        val quick = quickForPlugin(p)
        if (quick.descVi != null) return quick
        val original = quick.originalDesc ?: return quick
        val vi = translateToVi(original)
        return quick.copy(descVi = vi ?: original, autoTranslated = vi != null)
    }

    suspend fun fullForHit(hit: PluginManager.ModrinthHit, isPlugin: Boolean): AddonInfo {
        val quick = quickForHit(hit, isPlugin)
        if (quick.descVi != null) return quick
        val original = quick.originalDesc ?: return quick
        val vi = translateToVi(original)
        return quick.copy(descVi = vi ?: original, autoTranslated = vi != null)
    }

    // ── Mạng & bộ nhớ đệm ──

    private val netSlots = Semaphore(3)
    private val memTranslate = ConcurrentHashMap<String, String>()
    private val memProject = ConcurrentHashMap<String, String>()
    private val memSha = ConcurrentHashMap<String, String>()

    private fun cacheDir(): File {
        val base = System.getProperty("java.io.tmpdir") ?: "/data/local/tmp"
        return File(base, "minehost_addon_info_v1").apply { mkdirs() }
    }

    private fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fileSha1(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { ins ->
            val buf = ByteArray(16384)
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun translateToVi(text: String): String? = withContext(Dispatchers.IO) {
        val clean = text.trim().take(600)
        if (clean.isBlank()) return@withContext null
        val key = sha1Hex(clean.toByteArray())
        memTranslate[key]?.let { return@withContext it }
        val f = File(cacheDir(), "tr_$key.txt")
        if (f.isFile && f.length() > 0) {
            return@withContext runCatching { f.readText() }.getOrNull()?.also { memTranslate[key] = it }
        }
        val body = netSlots.withPermit {
            fetch("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=vi&dt=t&q=" + URLEncoder.encode(clean, "UTF-8"))
        } ?: return@withContext null
        val vi = runCatching {
            val root = JSONArray(body)
            val parts = root.getJSONArray(0)
            buildString {
                for (i in 0 until parts.length()) {
                    val seg = parts.optJSONArray(i) ?: continue
                    append(seg.optString(0))
                }
            }.trim()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@withContext null
        memTranslate[key] = vi
        runCatching { f.writeText(vi) }
        vi
    }

    private suspend fun modrinthProjectForJar(jarPath: String): JSONObject? = withContext(Dispatchers.IO) {
        val f = File(jarPath)
        if (!f.isFile || f.length() !in 256L..(64L * 1024 * 1024)) return@withContext null
        val shaKey = "${f.absolutePath}:${f.lastModified()}:${f.length()}"
        val sha = memSha[shaKey] ?: runCatching { fileSha1(f) }.getOrNull()?.also { memSha[shaKey] = it } ?: return@withContext null
        memProject[sha]?.let { return@withContext runCatching { JSONObject(it) }.getOrNull() }
        val cache = File(cacheDir(), "mr_$sha.json")
        if (cache.isFile && cache.length() > 2) {
            val txt = runCatching { cache.readText() }.getOrNull()
            if (txt != null) {
                memProject[sha] = txt
                return@withContext runCatching { JSONObject(txt) }.getOrNull()
            }
        }
        val projectJson = netSlots.withPermit {
            val ver = fetch("https://api.modrinth.com/v2/version_file/$sha?algorithm=sha1") ?: return@withPermit null
            val projectId = runCatching { JSONObject(ver).optString("project_id") }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return@withPermit null
            fetch("https://api.modrinth.com/v2/project/$projectId")
        } ?: run {
            // Không có trên Modrinth: lưu "{}" để khỏi hỏi lại liên tục
            runCatching { cache.writeText("{}") }
            return@withContext null
        }
        val slim = runCatching {
            val o = JSONObject(projectJson)
            JSONObject().apply {
                put("client_side", o.optString("client_side"))
                put("server_side", o.optString("server_side"))
                put("description", o.optString("description"))
                put("categories", o.optJSONArray("categories") ?: JSONArray())
            }.toString()
        }.getOrNull() ?: return@withContext null
        memProject[sha] = slim
        runCatching { cache.writeText(slim) }
        JSONObject(slim)
    }

    private fun fetch(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MineHost/2.6 (Android; github.com/duchilocket-oss/minehost-android)")
            }
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

// ── Giao diện dùng chung ──

@Composable
internal fun AddonSideBadge(side: AddonSide, modifier: Modifier = Modifier) {
    Text(
        side.label,
        color = side.color,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(side.color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

/** Nhãn Server/Client + mô tả tiếng Việt "mod này giúp làm gì". */
@Composable
internal fun AddonInfoLines(
    info: AddonInfo,
    modifier: Modifier = Modifier,
    descMaxLines: Int = 3,
    showSideExplain: Boolean = false,
    warnClientOnlyOnServer: Boolean = false
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AddonSideBadge(info.side)
        if (showSideExplain) {
            Text(info.side.explain, fontSize = 10.sp, color = info.side.color, lineHeight = 13.sp)
        }
        if (warnClientOnlyOnServer && info.side == AddonSide.CLIENT_ONLY) {
            Text(
                "⚠️ Mod chỉ dành cho máy người chơi — nên TẮT trên server để tránh lỗi khởi động.",
                fontSize = 10.sp,
                color = Color(0xFFEF4444),
                fontWeight = FontWeight.SemiBold,
                lineHeight = 13.sp
            )
        }
        val desc = info.descVi
        if (!desc.isNullOrBlank()) {
            Text(
                "💡 " + desc + if (info.autoTranslated) " (dịch tự động)" else "",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp,
                maxLines = descMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                "💡 Chưa có mô tả" + (info.categoriesVi?.let { " · Loại: $it" } ?: ""),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!desc.isNullOrBlank() && info.categoriesVi != null) {
            Text("🏷️ ${info.categoriesVi}", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun rememberModInfo(mod: PluginManager.ModEntry): AddonInfo {
    val state by produceState(AddonInfoVi.quickForMod(mod), mod.filePath, mod.fileName) {
        value = runCatching { AddonInfoVi.fullForMod(mod) }.getOrDefault(value)
    }
    return state
}

@Composable
internal fun rememberPluginInfo(plugin: PluginManager.InstalledPlugin): AddonInfo {
    val state by produceState(AddonInfoVi.quickForPlugin(plugin), plugin.filePath, plugin.fileName) {
        value = runCatching { AddonInfoVi.fullForPlugin(plugin) }.getOrDefault(value)
    }
    return state
}

@Composable
internal fun rememberHitInfo(hit: PluginManager.ModrinthHit, isPlugin: Boolean): AddonInfo {
    val state by produceState(AddonInfoVi.quickForHit(hit, isPlugin), hit.slug, isPlugin) {
        value = runCatching { AddonInfoVi.fullForHit(hit, isPlugin) }.getOrDefault(value)
    }
    return state
}
