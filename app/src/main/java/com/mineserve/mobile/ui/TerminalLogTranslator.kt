package com.mineserve.mobile.ui

object TerminalLogTranslator {
    private val ERROR_TAG_RE = Regex("""\[(?:[^\]]*/)?(?:ERROR|FATAL|SEVERE)\]""", RegexOption.IGNORE_CASE)
    private val WARN_TAG_RE = Regex("""\[(?:[^\]]*/)?WARN(?:ING)?\]""", RegexOption.IGNORE_CASE)

    fun translate(line: String): String {
        for ((key, vietnamese) in majorMappings) {
            if (line.contains(key, ignoreCase = true)) {
                return "$vietnamese\n  Gốc: $line"
            }
        }
        var updated = line
        for ((pattern, replacement) in regexMappings) {
            if (pattern.containsMatchIn(updated)) {
                updated = updated.replace(pattern, replacement)
            }
        }
        for ((en, vi) in inlineMappings) {
            if (updated.contains(en, ignoreCase = true)) {
                updated = updated.replace(Regex(Regex.escape(en), RegexOption.IGNORE_CASE), vi)
            }
        }
        if (updated != line) return updated
        return when {
            ERROR_TAG_RE.containsMatchIn(line) ->
                "Lỗi: $line\n  Gốc: $line"
            WARN_TAG_RE.containsMatchIn(line) ->
                "Cảnh báo: $line\n  Gốc: $line"
            else -> line
        }
    }

    // Các sự kiện quan trọng & lỗi kèm hướng dẫn khắc phục trực tiếp trên Console
    private val majorMappings = listOf(
        "Done (" to "Máy chủ đã khởi động hoàn tất! Bạn đã có thể vào game.",
        "Starting minecraft server" to "Đang khởi động máy chủ Minecraft",
        "Preparing level" to "Đang nạp thế giới (Map)",
        "Preparing spawn area" to "Đang chuẩn bị khu vực hồi sinh (Spawn)",
        "Preparing start region" to "Đang chuẩn bị vùng khởi đầu",
        "Building terrain" to "Đang tạo địa hình thế giới",
        "Stopping the server" to "Đang dừng máy chủ",
        "Stopping server" to "Đang dừng máy chủ",
        "Saving the game" to "Đang lưu dữ liệu thế giới",
        "Saved the game" to "Đã lưu thế giới hoàn tất",
        "Saving worlds" to "Đang lưu các thế giới",
        "Saving chunks" to "Đang lưu các vùng đất (Chunk)",
        "Saving players" to "Đang lưu dữ liệu người chơi",
        "Flushing chunk data" to "Đang ghi dữ liệu Chunk xuống bộ nhớ",
        "You need to agree to the EULA" to "Chưa đồng ý điều khoản Minecraft EULA -> Cách sửa: Bật công tắc EULA ở trang Tổng quan hoặc bấm Đồng ý khi được hỏi.",
        "Can't keep up" to "Máy chủ đang quá tải hoặc giật lag (TPS thấp) -> Cách sửa: Giảm tầm nhìn (view-distance xuống 6-8) trong Cấu hình máy chủ hoặc tăng RAM.",
        "Address already in use" to "Cổng mạng đã bị chiếm dụng -> Cách sửa: Đổi server-port trong Cấu hình máy chủ hoặc vào Môi trường -> Dọn tiến trình treo.",
        "BindException" to "Không thể mở cổng mạng (cổng đang bận) -> Cách sửa: Dừng máy chủ cũ đang chạy ngầm hoặc đổi cổng server-port.",
        "OutOfMemoryError" to "Tràn bộ nhớ Java (OutOfMemoryError) -> Cách sửa: Vào Cài đặt giảm giới hạn RAM (-Xmx) cho vừa bộ nhớ trống hoặc gỡ bớt Mod nặng.",
        "Java heap space" to "Không đủ bộ nhớ Heap cho Java -> Cách sửa: Điều chỉnh lại mức RAM (-Xmx) trong mục Cài đặt.",
        "UnsupportedClassVersionError" to "Phiên bản Java không tương thích với Core/Mod -> Cách sửa: Vào mục Môi trường -> chọn đúng phiên bản Java (MC 1.20.5+ cần Java 21, MC 1.18-1.20.4 cần Java 17, MC 1.16.5 cần Java 8).",
        "class file version" to "Phiên bản tệp class yêu cầu Java mới hơn -> Cách sửa: Vào mục Môi trường -> cài và chuyển sang Java 17 hoặc Java 21.",
        "mod loading has failed" to "Tải Mod thất bại hoặc sai phiên bản -> Cách sửa: Kiểm tra Mod thiếu thư viện phụ thuộc hoặc tắt Mod chỉ dành cho Client (OptiFine/Sodium/Iris) trong mục Mod & Plugin.",
        "Mixin apply failed" to "Lỗi xung đột Mixin giữa các Mod -> Cách sửa: Kiểm tra phiên bản Mod có khớp với phiên bản Minecraft hiện tại không.",
        "Could not load plugin" to "Không thể tải Plugin -> Cách sửa: Kiểm tra xem Plugin có đúng phiên bản Minecraft hoặc thiếu Plugin phụ thuộc (như Vault/PlaceholderAPI) không.",
        "Unsupported API version" to "Phiên bản API của Plugin không khớp với máy chủ -> Cách sửa: Tải phiên bản Plugin phù hợp với phiên bản MC của máy chủ.",
        "You are not white-listed" to "Người chơi chưa có trong danh sách trắng (Whitelist) -> Cách sửa: Tắt Whitelist hoặc thêm tên người chơi trong mục Người chơi.",
        "Authentication servers are down" to "Máy chủ xác thực Mojang không phản hồi -> Cách sửa: Nếu chơi tài khoản crack/offline, hãy tắt online-mode trong Cấu hình máy chủ.",
        "Failed to verify username" to "Không xác thực được tên người chơi -> Cách sửa: Vào Quản lý -> Cấu hình máy chủ -> tắt chế độ Bản quyền (online-mode=false).",
        "Internal Exception" to "Lỗi kết nối mạng nội bộ (Internal Exception)",
        "Connection refused" to "Kết nối bị từ chối -> Cách sửa: Kiểm tra máy chủ đã báo 'Hoàn tất' chưa và kiểm tra đúng cổng mạng.",
        "Connection reset" to "Kết nối mạng bị đặt lại (Connection reset)",
        "Timed out" to "Quá thời gian kết nối (Timed out) -> Cách sửa: Kiểm tra mạng hoặc đường hầm FRP/STUN.",
        "Network is unreachable" to "Mạng không thể truy cập -> Cách sửa: Kiểm tra kết nối Wi-Fi / 4G.",
        "No route to host" to "Không tìm thấy đường truyền tới máy chủ đích",
        "Unknown host" to "Không phân giải được tên miền máy chủ (Unknown host) -> Cách sửa: Kiểm tra kết nối Internet/DNS.",
        "Broken pipe" to "Đường truyền kết nối bị ngắt (Broken pipe)",
        "Permission denied" to "Từ chối quyền truy cập tệp (Permission denied) -> Cách sửa: Vào mục Môi trường -> bấm Sửa chữa môi trường.",
        "No space left on device" to "Bộ nhớ điện thoại đã đầy -> Cách sửa: Giải phóng dung lượng trống trên điện thoại và xóa bớt bản sao lưu cũ.",
        "Read-only file system" to "Hệ thống tệp đang ở chế độ chỉ đọc",
        "File not found" to "Không tìm thấy tệp hoặc thư mục yêu cầu",
        "No such file" to "Tệp hoặc thư mục không tồn tại -> Cách sửa: Kiểm tra lại Core hoặc bấm Sửa chữa môi trường.",
        "Not a directory" to "Đường dẫn không phải là thư mục",
        "StackOverflowError" to "Lỗi tràn ngăn xếp (StackOverflowError) do vòng lặp vô hạn trong Mod/Plugin",
        "NoClassDefFoundError" to "Thiếu thư viện phụ thuộc (NoClassDefFoundError) -> Cách sửa: Cài thêm Mod/Plugin thư viện (Library/API) mà Mod yêu cầu.",
        "NoSuchMethodError" to "Xung đột phiên bản thư viện (NoSuchMethodError) -> Cách sửa: Đảm bảo các Mod/Plugin dùng đúng phiên bản Minecraft của máy chủ.",
        "Exception in thread" to "Phát sinh ngoại lệ trong luồng thực thi",
        "Caused by" to "Nguyên nhân gốc do",
        "The server crashed" to "Máy chủ đã bị sập (Crash) -> Cách sửa: Xem mục Sự cố (Crash) ở trang Quản lý để đọc chẩn đoán chi tiết.",
        "Minecraft has crashed" to "Minecraft đã gặp sự cố dừng đột ngột -> Cách sửa: Kiểm tra thẻ Sự cố để xem nguyên nhân và gợi ý sửa.",
        "Saving crash report" to "Đang lưu báo cáo sự cố (Crash Report)",
        "Corruption detected" to "Phát hiện dữ liệu thế giới bị lỗi -> Cách sửa: Khôi phục từ bản sao lưu gần nhất trong mục Sao lưu.",
        "Level is corrupt" to "Thế giới (Map) bị hỏng không thể tải -> Cách sửa: Khôi phục bản sao lưu hoặc nhập lại tệp ZIP của Map.",
        "Unable to load world" to "Không thể nạp thế giới -> Cách sửa: Kiểm tra định dạng Map và phiên bản Minecraft.",
        "Failed to load world" to "Tải thế giới thất bại -> Cách sửa: Đảm bảo đã cài đủ các Mod của Map trước khi bật máy chủ.",
        "Loading plugins" to "Đang nạp danh sách Plugin",
        "Enabling plugin" to "Đang kích hoạt Plugin",
        "Disabling plugin" to "Đang tắt Plugin",
        "Failed to start the minecraft server" to "Khởi động máy chủ Minecraft thất bại -> Cách sửa: Xem dòng lỗi phía trên (thường do sai phiên bản Java, trùng cổng hoặc lỗi Mod).",
        "Failed to start" to "Khởi động thất bại",
        "Shutting down" to "Đang đóng máy chủ",
        "Closing threads" to "Đang đóng các luồng xử lý",
        "Closing server" to "Đang đóng máy chủ",
        "Deleting level" to "Đang xóa dữ liệu thế giới",
        "Whitelist has been enabled" to "Đã bật danh sách trắng (Whitelist)",
        "Whitelist has been disabled" to "Đã tắt danh sách trắng (Whitelist)",
        "The server is full" to "Máy chủ đã đầy, không thể vào thêm -> Cách sửa: Tăng max-players trong Cấu hình máy chủ.",
        "RCON not running" to "Chưa bật điều khiển từ xa RCON",
        "Server started" to "Máy chủ đã bắt đầu chạy",
        "Server is running in offline mode" to "Máy chủ đang chạy ở chế độ Offline (cho phép tài khoản không bản quyền vào)",
        "RCON running on" to "Đã bật cổng điều khiển RCON tại",
        "Starting GS4 status listener" to "Đã bật bộ lắng nghe trạng thái GS4",
        "Started query engine" to "Đã khởi động bộ truy vấn trạng thái",
        "Starting server" to "Đang khởi chạy máy chủ",
        "Loading properties" to "Đang đọc cấu hình server.properties",
        "Loading server properties" to "Đang đọc cấu hình server.properties"
    )

    private val inlineMappings = listOf(
        "Unloading world" to "Đang đóng thế giới",
        "Loading world" to "Đang tải thế giới",
        "Unloading level" to "Đang đóng thế giới",
        "Loading level" to "Đang tải thế giới",
        "Preparing spawn" to "Đang chuẩn bị điểm hồi sinh",
        "Preparing start region" to "Đang chuẩn bị vùng khởi đầu",
        "Listening on" to "Đang lắng nghe kết nối tại",
        "Done loading" to "Đã tải xong",
        "Loaded plugin" to "Đã nạp xong Plugin",
        "Loaded" to "Đã nạp",
        "Saving level" to "Đang lưu thế giới",
        "Reloading" to "Đang tải lại",
        "Reload complete" to "Đã tải lại hoàn tất",
        "Loading plugins" to "Đang tải Plugin",
        "Enabling plugin" to "Đang bật Plugin",
        "Disabling plugin" to "Đang tắt Plugin",
        "Plugin already initialized" to "Plugin đã được khởi tạo trước đó",
        "Failed to load" to "Tải thất bại",
        "Failed to enable" to "Kích hoạt thất bại",
        "Could not connect" to "Không thể kết nối",
        "Connection lost" to "Mất kết nối",
        "Login successful" to "Đăng nhập thành công",
        "Invalid username" to "Tên người chơi không hợp lệ",
        "You have been kicked" to "Bạn đã bị đuổi khỏi máy chủ",
        "You have been banned" to "Bạn đã bị cấm khỏi máy chủ",
        "Server is full" to "Máy chủ đã đầy",
        "Unknown command" to "Lệnh không tồn tại",
        "Incorrect argument" to "Sai cú pháp tham số lệnh",
        "Usage:" to "Cách dùng:",
        "Permission denied" to "Không đủ quyền hạn",
        "No permission" to "Không đủ quyền hạn"
    )

    private val regexMappings = listOf(
        Regex("(\\w+) joined the game") to "Người chơi $1 đã tham gia trò chơi",
        Regex("(\\w+) left the game") to "Người chơi $1 đã rời khỏi trò chơi",
        Regex("There are (\\d+) of a max of (\\d+) players online") to "Hiện có $1/$2 người chơi đang online"
    ) + listOf(
        "lost connection" to "mất kết nối",
        "disconnected" to "đã ngắt kết nối",
        "was banned" to "đã bị cấm (ban)",
        "was unbanned" to "đã được gỡ lệnh cấm",
        "was kicked" to "đã bị đuổi khỏi máy chủ",
        "whispers to you" to "thì thầm với bạn",
        "whispered to you" to "đã thì thầm với bạn",
        "moved too quickly" to "di chuyển quá nhanh",
        "Illegal characters in chat" to "Ký tự không hợp lệ trong tin nhắn",
        "is already connected" to "đang kết nối sẵn trong máy chủ",
        "Failed to login" to "Đăng nhập thất bại",
        "Cannot join server" to "Không thể vào máy chủ",
        "Server closed" to "Máy chủ đã đóng",
        "Autosaved" to "Đã tự động lưu",
        "Auto-saved" to "Đã tự động lưu",
        "Unable to save world" to "Không thể lưu thế giới",
        "Default game type" to "Chế độ chơi mặc định",
        "Spawn radius" to "Bán kính điểm hồi sinh",
        "Difficulty" to "Độ khó",
        "players online" to "người chơi đang online",
        "joined the game" to "đã tham gia trò chơi",
        "left the game" to "đã rời khỏi trò chơi",
        "has joined" to "đã vào máy chủ",
        "has left" to "đã rời máy chủ",
        "logged in" to "đã đăng nhập",
        "logged out" to "đã đăng xuất",
        "Starting" to "Đang khởi động",
        "Loading" to "Đang tải",
        "Loaded" to "Đã tải",
        "Preparing" to "Đang chuẩn bị",
        "Stopping" to "Đang dừng",
        "Saving" to "Đang lưu",
        "Saved" to "Đã lưu",
        "Enabled" to "Đã bật",
        "Disabled" to "Đã tắt",
        "successfully" to "thành công",
        "failed" to "thất bại",
        "online" to "trực tuyến",
        "offline" to "ngoại tuyến"
    ).map { (en, vi) ->
        Regex("\\b" + Regex.escape(en) + "\\b", RegexOption.IGNORE_CASE) to vi
    }
}
