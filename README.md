# 🇻🇳 MineHost Android — v2.8.2 (Build 49 • Bản Cộng Đồng Chính Thức)

[![Phiên bản mới nhất](https://img.shields.io/badge/Release-v2.8.2%20(Build%2049)-2E7D32?style=for-the-badge&logo=android)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![Nền tảng](https://img.shields.io/badge/Android-8.0%2B%20(ARM64)-0D9488?style=for-the-badge)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases)
[![Cập nhật OTA](https://img.shields.io/badge/OTA-C%C3%A0i%20%C4%91%C3%A8%20100%25%20%26%20T%E1%BB%B1%20x%C3%B3a%20APK-2563EB?style=for-the-badge)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![Giấy phép](https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge)](LICENSE)

**MineHost** (`vn.minehost.pro`) là ứng dụng Android mã nguồn mở giúp bạn **tự tạo, vận hành và quản trị máy chủ Minecraft Java & Bedrock (Chơi chung PC + Điện thoại PE)** trực tiếp ngay trên điện thoại Android mà không cần thuê VPS.

👉 **[📥 TẢI NGAY APK MỚI NHẤT (minehost-2.8.2-arm64.apk)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)**

---

## 🔥 Điểm Mới Nổi Bật Trong Bản `v2.8.2` (Build 49)

### 1. ⚡ Giao Diện Trang Chủ 3 Phân Mục Trượt Ngang Gọn Mượt
- Ngay bên dưới thẻ điều khiển Server chính là thanh chuyển nhanh **3 khu vực chuyên biệt** (giảm hơn **70%** độ dài cuộn trang, tốc độ vuốt mượt 60–120 FPS):
  - **`🌐 Kết nối & Thẻ mời`**: Đường hầm Playit.gg (TCP + UDP), Chơi chung PC + PE, Tạo Ảnh Thẻ Mời HD & 4 ô Quản trị nhanh.
  - **`⚡ Tối ưu & Bảo vệ Pin`**: Tối ưu RAM 1 chạm, Gói 5 Mod Siêu Nhẹ (`-45% RAM`), Quét lỗi thiếu thư viện Mod, Bảo vệ Pin/Nhiệt độ tự động & Chẩn đoán phần cứng.
  - **`🎮 Thế giới & VIP`**: Công tắc Luật Thế Giới (Gamerules), Thời gian/Thời tiết, Cấp quyền & Thao tác người chơi 1 chạm, Danh hiệu VIP, Sự kiện vui nhộn, Radar `/locate`, Sổ tay dịch chuyển (Warp Book) & Web Remote `:8585`.
  - **`📋 Xem tất cả`**: Hiển thị toàn bộ công cụ trên một trang liền mạch.

### 2. 🛡️ Bảo Vệ Map Tự Động Khi Điện Thoại Yếu Pin (`≤ 10%`) Hoặc Quá Nóng (`≥ 45.0°C`)
- Hoạt động trực tiếp trong dịch vụ nền (`McForegroundService`) kể cả khi tắt màn hình hoặc thoát ứng dụng:
  - Khi pin tụt xuống **`≤ 10%` (chưa cắm sạc)** hoặc nhiệt độ máy chạm ngưỡng **`≥ 45.0°C`**, hệ thống tự động phát cảnh báo chữ lớn trong game, đếm ngược 10 giây, chạy `save-all` 2 lần rồi dừng Server an toàn để chống lỗi hỏng Map (Corrupt World / Rollback đồ) do sập nguồn đột ngột.
  - Tự động dọn rác giảm tải CPU định kỳ khi máy ấm trên `42.5°C`.

### 3. 📲 Thanh Thông Báo Nền Thông Minh (Live Rich Notification)
- Hiển thị trực tiếp theo thời gian thực ngay trên thanh thông báo kéo xuống của điện thoại:
  - **`🟢 Tên Server • Số người chơi online • TPS • RAM • Pin & Nhiệt độ • Địa chỉ IP`**
- Tích hợp **3 nút điều khiển nhanh 1 chạm** không cần mở ứng dụng:
  - **`📋 Copy IP`** *(Sao chép ngay địa chỉ Server vào bộ nhớ tạm)*
  - **`🧹 Dọn RAM`** *(Xóa vật phẩm rơi dưới đất + `save-all` + giải phóng bộ nhớ)*
  - **`🛑 Lưu & Tắt`** *(Lưu thế giới an toàn và dừng Server)*

### 4. 🧩 Tự Động Phát Hiện Mod Thiếu Thư Viện Phụ Trợ & Tải Bù 1 Chạm (Auto-Dependency Fixer)
- Tự động phân tích log khởi động của **Fabric / Forge / NeoForge / Quilt** để nhận diện chính xác các thư viện bắt buộc còn thiếu (`Fabric API`, `Cloth Config`, `Architectury API`, `GeckoLib`, `Curios`, `Trinkets`, `YACL`, `Balm`, `Bookshelf`, `Moonlight Lib`, `Kotlin for Forge`, `Puzzles Lib`...).
- Hiển thị thẻ cảnh báo kèm nút **`⬇️ Tải & Cài Bù Ngay + Bật Lại SV`** — tự động tải đúng phiên bản từ Modrinth vào thư mục `mods/` và khởi động lại máy chủ chỉ với 1 chạm.

### 5. 🖼️ Tạo Ảnh Thẻ Mời Server Đẹp Chuẩn HD (`1080×640`) & Chọn Người Chơi 1 Chạm
- **Ảnh Thẻ Mời Server 1 Chạm:** Tự động vẽ tấm ảnh Banner độ phân giải cao gồm Tên Server, Trạng thái Online, Phiên bản, **Địa chỉ Máy tính (Java)** và **Địa chỉ + Cổng Điện thoại (Bedrock PE)** để gửi thẳng vào nhóm Zalo / Messenger / Discord.
- **Chọn Nhanh Người Chơi Trong Server 1 Chạm (Không Cần Nhập Tay):** Toàn bộ thao tác cấp quyền `👑 OP`, `🚫 Gỡ OP`, `🛡️ Whitelist`, `👢 Kick`, `🔨 Ban`, `Đổi chế độ chơi`, `Túi đồ & EnderChest`, `Danh hiệu VIP` và `Dịch chuyển` trên Trang chủ, tab Người chơi và Terminal đều hiện sẵn danh sách chip người chơi (Online + Lịch sử + OP + Whitelist) để bấm chọn ngay.

### 6. 🔄 Cập Nhật Tự Động Nhanh Nhất (OTA) & Tự Động Xóa File APK Sau Khi Cài
- Phát hiện bản cập nhật mới trên GitHub Releases ngay lập tức khi mở ứng dụng (`0ms`), cho phép chọn **🚀 Cập nhật** (cài đè giữ nguyên 100% Server, Map & Mod) hoặc **Bỏ qua**.
- **🧹 Tự động xóa sạch file APK sau khi cập nhật xong:** Ngay khi Android hoàn tất cài đặt đè (`ACTION_MY_PACKAGE_REPLACED`), ứng dụng tự động xóa file `.apk` / `.part` trong bộ nhớ đệm và thư mục `Download` của điện thoại để giải phóng dung lượng máy.

---

## ⚡ Tính Năng Cốt Lõi Cho Máy Cấu Hình Yếu & Chơi Chung PC + PE

- **🪶 Chế độ Máy RAM Ít Siêu Nhẹ & Gói 5 Mod Sinh Tồn (`-45% RAM`):** Tự động tối ưu `view-distance=4`, `simulation-distance=3` và cài 5 mod nhẹ nhất: **FerriteCore**, **Lithium**, **Tree Harvester**, **Veinminer**, **Universal Graves**.
- **🤝 Chơi Chung PC (Java) + Điện Thoại (Bedrock PE) 1 Chạm:** Tự động cài đặt và cấu hình đồng bộ **GeyserMC + Floodgate + ViaVersion + ViaBackwards**, hỗ trợ kiểm tra tương thích phiên bản PE trực tiếp.
- **🌍 Đường Hầm Playit.gg Kép (TCP + UDP):** Mở đồng thời cổng TCP cho máy tính (Java / PojavLauncher) và cổng UDP riêng cho điện thoại (Minecraft PE) để chơi chung từ xa qua 4G / khác mạng Wi-Fi không cần mở port router.
- **🌙 Màn Hình Đen Treo Server (OLED Keep-Alive) & Tự Ngủ Đông (Auto-Sleep):** Tiết kiệm 80% pin màn hình khi treo máy qua đêm và tự động lưu map + tắt server khi không có người chơi.

---

## 🚀 Hướng Dẫn Bắt Đầu Nhanh (3 Bước)

1. **Bước 1 — Cài môi trường chạy:** Mở ứng dụng → tại tab **Trang chủ**, bấm **Cài môi trường chạy** (chỉ cần thực hiện 1 lần đầu).
2. **Bước 2 — Tạo Server Core & Java:** Chọn nhân **Paper** (khuyên dùng nếu muốn chơi chung PC + PE) hoặc **Fabric** (nếu muốn cài Mod sinh tồn) → ứng dụng tự động tải Core và gợi ý phiên bản Java tương thích (Java 8 / 17 / 21 / 25).
3. **Bước 3 — Bật Server & Mời bạn bè:**
   - Bấm **BẬT SERVER NGAY** và đồng ý [Minecraft EULA](https://www.minecraft.net/eula).
   - **Chơi cùng Wi-Fi:** Sao chép địa chỉ IP LAN hoặc bấm **🖼️ Tạo Ảnh Thẻ Mời Server Đẹp**.
   - **Chơi khác mạng / 4G:** Bấm **🚀 Bật Playit.gg** trên Trang chủ để lấy địa chỉ cho cả PC và Điện thoại PE.

---

## ⚖️ Bản Quyền, Quyền Riêng Tư & Giấy Phép

- **Giấy phép mã nguồn mở:** Phát hành theo giấy phép [GNU GPL-3.0-or-later](LICENSE), kế thừa nền tảng runtime từ dự án mã nguồn mở [Minexa](https://github.com/lovehutaocute/Minexa) ([NOTICE](NOTICE)).
- **Chính sách Quyền riêng tư:** Cam kết **Zero Telemetry** — không thu thập bất kỳ dữ liệu cá nhân nào. Xem chi tiết tại [PRIVACY.md](PRIVACY.md).
- **Tuyên bố miễn trừ thương hiệu Mojang / Microsoft:**
  > **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**  
  > *(Không phải sản phẩm Minecraft chính thức. Không được phê duyệt bởi hoặc liên kết với Mojang hay Microsoft.)*
