# 🇻🇳 MineHost Android — v2.8.4 (Build 51 • Bản Cộng Đồng Chính Thức)

[![Phiên bản mới nhất](https://img.shields.io/badge/Release-v2.8.4%20(Build%2051)-2E7D32?style=for-the-badge&logo=android)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![Nền tảng](https://img.shields.io/badge/Android-8.0%2B%20(ARM64)-0D9488?style=for-the-badge)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases)
[![Cập nhật OTA](https://img.shields.io/badge/OTA-C%C3%A0i%20%C4%91%C3%A8%20100%25%20%26%20T%E1%BB%B1%20x%C3%B3a%20APK-2563EB?style=for-the-badge)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![Giấy phép](https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge)](LICENSE)

**MineHost** (`vn.minehost.pro`) là ứng dụng Android mã nguồn mở giúp bạn **tự tạo, vận hành và quản trị máy chủ Minecraft Java & Bedrock (Chơi chung PC + Điện thoại PE)** trực tiếp ngay trên điện thoại Android mà không cần thuê VPS.

👉 **[📥 TẢI NGAY APK MỚI NHẤT (minehost-2.8.4-arm64.apk)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)**

---

## 🔥 Nâng Cấp Mới Nhất Trong Bản `v2.8.4` (Build 51) & `v2.8.3`

### 🆕 0. 🩺 Việt Hóa 100% Hệ Thống Báo Lỗi Crash & Giao Diện Phân Tích Sự Cố Trực Quan (`v2.8.4`)
- **Xóa sạch 100% chữ Trung Quốc còn sót** trong 18 quy tắc chẩn đoán lỗi (`server_errors.json`), trình tạo file báo cáo (`CrashReportManager`), bộ phân tích (`CrashReportAnalyzer`) và nhật ký hệ thống (`mineserve.log`), đồng thời **tự động dịch cả các file báo cáo lỗi cũ** đã lưu trên máy.
- **Thẻ Chẩn Đoán Sự Cố Mới:** Tách bạch rõ **Nguyên nhân gốc**, **Mã thoát tiến trình** (`137 = Tràn RAM / Hệ thống buộc dừng`, `1 = Lỗi cấu hình/Java`), **Bằng chứng log** và hộp **`💡 Cách khắc phục`** nổi bật.
- **Nút `🔍 Xem Phân Tích Lỗi Sập` ngay trên Thẻ Server Chính** khi khởi động thất bại + **Thu gọn bảng Chẩn đoán 6 bước** trên Trang chủ giúp nút Copy IP PC/PE hiển thị ngay lập tức.

### 1. 🎯 Bong Bóng Menu Admin Nổi Ngay Trong Lúc Đang Chơi Game (Floating In-Game Admin Menu)
- Khi vừa mở Server vừa vào chơi **Minecraft PE / PojavLauncher** trên cùng 1 điện thoại, một bong bóng nhỏ gọn (`⚡ SV CPU | RAM ▾`) nổi ở mép màn hình (kéo thả tự do).
- Chạm vào bong bóng để bung ngay **6 nút Admin 1 chạm** mà không cần thoát game:
  - **`☀️ Trời Sáng`** *(Chỉnh ngày sáng & tạnh mưa tức thì)*
  - **`🧹 Dọn Lag`** *(Xóa vật phẩm rơi + lưu map + giải phóng RAM)*
  - **`❤️ Hồi Máu @a`** *(Hồi đầy máu & thức ăn cho cả nhóm)*
  - **`📋 Copy IP`** *(Sao chép nhanh địa chỉ Server)*
  - **`🏠 Mở App`** *(Quay lại bảng điều khiển MineHost)*
  - **`🛑 Lưu & Tắt`** *(Chạy `save-all` và tắt Server an toàn)*

### 2. 💬 Khung Chat Trực Tiếp 2 Chiều Với Người Chơi Trong Game (Live Game Chat Messenger)
- Tự động lọc riêng tin nhắn trò chuyện và thông báo vào/ra của người chơi trong game thành khung hội thoại kiểu Messenger/Zalo ngay trên Trang chủ (tự động loại bỏ sạch các dòng log kỹ thuật rối mắt).
- Cho phép chủ host gõ tin nhắn trả lời trực tiếp vào trong game với danh hiệu màu vàng nổi bật **`[👑 Chủ Server]`**.

### 3. 📦 Cài Gói Modpack `.mrpack` (Modrinth) 1 Chạm & Tự Lọc Bỏ Mod Đồ Họa Client
- Chọn bất kỳ tệp Modpack `.mrpack` nào tải từ Modrinth -> ứng dụng tự động giải nén `overrides/` / `server-overrides/`, đọc `modrinth.index.json` và tải toàn bộ Mod Server vào `mods/`.
- **Tự động lọc bỏ các Mod chỉ dành cho Client** (`Sodium`, `Iris`, `OptiFine`, `ModMenu`, `EntityCulling`, `ImmediatelyFast`, `env.server == "unsupported"`...) để ngăn chặn tuyệt đối lỗi văng Server khi cài gói Modpack!

### 4. 🔄 Tự Động Bật Lại Khi Văng (Auto-Restart) & ⏪ Hoàn Tác Mod Vừa Cài Gây Lỗi 1 Chạm
- **Auto-Restart on Crash:** Tự động khởi động lại Server nếu bị văng đột ngột giữa chừng (giới hạn tối đa 3 lần liên tiếp để bảo vệ máy).
- **1-Tap Mod Rollback (`⏪ Hoàn Tác Mod Vừa Cài`):** Nếu vừa cài thêm Mod mới mà làm Server báo lỗi không lên được, chỉ cần bấm 1 nút để tự động tạm tắt (`.disabled`) các Mod vừa thêm gần nhất và mở lại Server an toàn.

### 5. 🛡️ Lá Chắn Chống Bot & Spam Kết Nối (Anti-Bot & Connection Flood Guard)
- Tự động giám sát luồng kết nối khi mở đường hầm công khai (Playit.gg / FRP): nếu phát hiện 1 địa chỉ IP bên ngoài spam **`≥ 6` kết nối trong 15 giây** hoặc dùng **`≥ 3` tên nhân vật ảo khác nhau từ cùng 1 IP trong 15 giây**, hệ thống tự động thực thi `ban-ip` ngay lập tức để bảo vệ TPS và chống sập Server.

---

## ⚡ Các Tính Năng Nổi Bật Đã Có Từ `v2.8.1` – `v2.8.2`

- **⚡ Giao Diện Trang Chủ 3 Phân Mục Trượt Ngang Gọn Mượt:** Chuyển nhanh giữa `🌐 Kết nối & Thẻ mời`, `⚡ Tối ưu & Bảo vệ Pin`, `🎮 Thế giới & VIP` và `📋 Xem tất cả` (giảm hơn 70% độ dài cuộn trang).
- **🛡️ Bảo Vệ Map Tự Động Khi Điện Thoại Yếu Pin (`≤ 10%`) Hoặc Quá Nóng (`≥ 45.0°C`):** Tự động cảnh báo trong game, chạy `save-all` 2 lần rồi dừng Server an toàn trước khi điện thoại sập nguồn.
- **📲 Thanh Thông Báo Nền Thông Minh (Live Rich Notification):** Hiện trực tiếp `Tên Server • Online • TPS • RAM • Pin & Nhiệt độ • IP` kèm 3 nút `📋 Copy IP`, `🧹 Dọn RAM`, `🛑 Lưu & Tắt`.
- **🧩 Tự Động Phát Hiện Mod Thiếu Thư Viện Phụ Trợ & Tải Bù 1 Chạm:** Tự động nhận diện và tải bù `Fabric API`, `Cloth Config`, `Architectury API`, `GeckoLib`, `Curios`, `Trinkets`, `YACL`, `Balm`, `Kotlin for Forge`...
- **🖼️ Tạo Ảnh Thẻ Mời Server Đẹp Chuẩn HD (`1080×640`) & Chọn Người Chơi 1 Chạm:** Vẽ ảnh Banner mời bạn bè gồm cả IP PC + Điện thoại PE và chọn người chơi 1 chạm không cần nhập tay.
- **🔄 Cập Nhật OTA Cài Đè 100% & Tự Xóa File APK Sau Khi Cài:** Phát hiện bản mới trong `0ms` và tự động dọn sạch file `.apk` sau khi cập nhật hoàn tất.

---

## ⚖️ Bản Quyền, Quyền Riêng Tư & Giấy Phép

- **Giấy phép mã nguồn mở:** Phát hành theo giấy phép [GNU GPL-3.0-or-later](LICENSE), kế thừa nền tảng runtime từ dự án mã nguồn mở [Minexa](https://github.com/lovehutaocute/Minexa) ([NOTICE](NOTICE)).
- **Chính sách Quyền riêng tư:** Cam kết **Zero Telemetry** — không thu thập bất kỳ dữ liệu cá nhân nào. Xem chi tiết tại [PRIVACY.md](PRIVACY.md).
- **Tuyên bố miễn trừ thương hiệu Mojang / Microsoft:**
  > **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**  
  > *(Không phải sản phẩm Minecraft chính thức. Không được phê duyệt bởi hoặc liên kết với Mojang hay Microsoft.)*
