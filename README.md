# MineHost Android — 0.1.0-alpha.2

Ứng dụng Android tự host Minecraft và quản lý server trên điện thoại.
**Bản thử nghiệm, chưa xác minh trên thiết bị thật. Không cam kết host 24/7.**

Mã nguồn và build nằm trên GitHub, không cần clone/build trên máy tính của chủ repo.
Server, Java và world vẫn cần dung lượng trên điện thoại.
GitHub Actions chỉ build/test APK, **không dùng làm máy host Minecraft**.

## Giao diện

5 mục: **Tổng quan · Console · Kho cài đặt · Tệp · Quản lý**.

- Tổng quan tiếng Việt: trạng thái thật, bật/dừng/khởi động lại, RAM, IP LAN.
- Kho cài đặt: core server, plugin/mod, Java, hướng dẫn Java + Bedrock.
- Quản lý: server, người chơi/whitelist/OP/ban, world, server.properties, backup, mạng, lịch, chẩn đoán.
- Màn hình chi tiết kế thừa còn tiếng Anh và một phần tiếng Trung; chưa Việt hóa 100%.
- Màu xanh lá, giao diện sáng/tối theo hệ thống.

## Nhập world và tối ưu

- Tổng quan hoặc Quản lý → **Nhập thế giới có sẵn** → ZIP → xác nhận server/tên → xem kết quả → **Dùng world này**.
- ZIP chứa level.dat ở gốc hoặc trong thư mục world/, có thể kèm world_nether/world_the_end. Không tự chuyển Bedrock sang Java hay đổi phiên bản/modpack/cách bố trí chiều.
- Giới hạn: ZIP 2 GB, giải nén 4 GB, 100.000 tệp; giữ 256 MB dự phòng. Kiểm tra đường dẫn, loại world, dung lượng; giải nén thư mục tạm, không ghi đè world cũ. Không tự kích hoạt.
- World và runtime vẫn nằm trên điện thoại, không tải lên GitHub.
- Chỉ cài môi trường khi người dùng chọn. Mặc định tắt keepalive lúc chưa chạy server. Bỏ đo tài nguyên định kỳ lúc dừng và bỏ quét thư mục world định kỳ.
- RAM tự động là lựa chọn chủ động, không đổi cấu hình RAM thủ công cũ. Kiểm tra RAM trống mỗi lần JVM bật; lệnh khởi động tùy chỉnh tự quản RAM.
- CPU, nhiệt độ, chunk mới và plugin/mod ảnh hưởng TPS. Chưa đo độ mượt trên điện thoại.

## Nguồn gốc

Fork tùy biến từ [Minexa](https://github.com/lovehutaocute/Minexa),
commit **d66af822822e7538f8520fd79378eecc93f3ab57**.
Giữ [GPL-3.0-or-later](LICENSE), [NOTICE](NOTICE) và bản quyền upstream.
Runtime, quản lý server, console, tệp, backup và nhiều kiểm thử được kế thừa, không viết lại từ đầu.

Android **8+ (API 26)**, **ARM64**. App ID: vn.minehost.android.
Target SDK 28 phục vụ runtime native của upstream: APK cài trực tiếp, chưa phù hợp phát hành Google Play.
ROM/Android mới có thể hạn chế thực thi, lưu trữ hoặc chạy nền; cần kiểm thử riêng.

## Khởi động

1. Lấy APK thử nghiệm: **Actions → MineHost Android checks and APK → Artifacts**.
2. Tổng quan → Cài môi trường chạy. Giữ app mở và dùng Wi-Fi.
3. Kho cài đặt → Core Minecraft → chọn Paper hoặc Fabric và phiên bản.
4. Cài Java phù hợp. Paper 1.20–1.21.11 dùng Java 21; Paper 26.1+ dùng Java 25. Core khác xem tài liệu riêng.
5. Tổng quan → Bật server → tự đọc và quyết định đồng ý Minecraft EULA.
6. Tổng quan → Tối ưu theo điện thoại: chọn RAM tự động hoặc giữ chỉnh tay. Máy 6 GB thường gợi ý khoảng 1,75–2 GB, còn giảm theo RAM trống lúc bật. Nút Áp dụng cấu hình nhẹ chỉnh view-distance, simulation-distance và max-players, sao chép server.properties cũ trước.
7. Cùng Wi-Fi: dùng địa chỉ LAN trong app. Khác mạng: cần cổng/tunnel phù hợp; CGNAT có thể ngăn port forwarding.

Không cần thuê VPS khi điện thoại trực tiếp host. Tên miền/VPS không được cấp kèm.
Không mở public console, FTP hay quản trị MCP mặc định.

## Plugin, mod và Bedrock

- Paper/Bukkit dùng plugin; Fabric/Forge/NeoForge/Quilt dùng đúng mod và phiên bản.
- Dừng server, sao lưu trước khi cài. JAR được staging và kiểm tra trước khi thay thế.
- Chặn OptiFine nhận diện qua package, Fabric environment=client và một số sai loader.
  Đây không phải quét mã độc hay kiểm chứng tương thích hoàn chỉnh.
- Modrinth tải theo phiên bản + loader chính xác, không rơi về bản mới nhất bất kể tương thích.
- **Chưa tự giải dependency.** Nếu Modrinth khai báo dependency bắt buộc, alpha yêu cầu tải bộ JAR từ trang dự án rồi nhập thủ công.
- **Geyser/Floodgate hiện là hướng dẫn + nhập JAR thủ công**, chưa có cài/config một chạm.
  Xem [Geyser](https://geysermc.org/wiki/geyser/setup/) và [Floodgate](https://geysermc.org/wiki/floodgate/).
- OptiFine/shader cài ở máy chơi Java, không trên server.
- Mod cần Java client riêng không tự tương thích với Bedrock.

## An toàn và giới hạn

- Không tự đồng ý EULA; lưu xác nhận theo server. Lịch chạy và crash-restart cũng phải có xác nhận.
- Giữ Java online-mode=true. Không chia sẻ token tunnel, key Floodgate hoặc log chứa bí mật.
- Một tiến trình Minecraft; cài JAR và khởi động dùng khóa chung để tránh ghi trong lúc bắt đầu chạy.
- Plugin/mod chạy mã với quyền app: chỉ tải nguồn tin cậy.
- Termux/PRoot, tải phụ thuộc và mirror được kế thừa; chưa audit bảo mật toàn bộ.
- Backup chưa tự đưa lên cloud. Xuất backup khỏi vùng dữ liệu app trước khi gỡ cài đặt.
- Từ alpha.2: APK release không debuggable, R8/resource shrinking, khóa ký cố định trong Actions Secrets. CI đối chiếu chứng thư và từ chối tự tạo khóa thay thế.
- **Alpha.1 không cài đè lên alpha.2 được** do khóa cũ không được lưu. Xem [Chuyển bản](MIGRATION.md). Xuất và kiểm tra sao lưu trước khi gỡ alpha.1.
- Repo riêng tư cần đăng nhập GitHub để tải artifact. Không nhúng token vào APK.
- Lint kế thừa chưa chặn build khi có lỗi; đọc báo cáo, không coi build thành công là chứng nhận bảo mật.

## Kiểm thử

Workflow chạy unit tests/Robolectric, lintRelease và assembleRelease trên runner GitHub.
Artifact gồm APK, SHA-256, SHA commit thực tế và báo cáo.
Xem trạng thái tại [Actions](https://github.com/duchilocket-oss/minehost-android/actions).

Cần kiểm thử ARM64 thật: tải Java/core; bật/dừng/restart; hai máy vào LAN;
plugin/mod đúng/sai; backup/restore; màn hình tắt; mất Wi-Fi; nóng máy; Geyser và nâng cấp APK.

## Tiếp theo

Việt hóa màn hình chi tiết; wizard Paper/Fabric; dependency resolver;
Geyser/Floodgate có kiểm tra tương thích; kiểm thử thiết bị.

