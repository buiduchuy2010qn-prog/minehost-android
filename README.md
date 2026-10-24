# 🇻🇳 MineHost Android — v2.7.4 (Bản Cộng Đồng Chính Thức)

[![Phiên bản mới nhất](https://img.shields.io/badge/Release-v2.7.4-2E7D32?style=for-the-badge&logo=android)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![Nền tảng](https://img.shields.io/badge/Android-8.0%2B%20(ARM64)-0D9488?style=for-the-badge)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases)
[![Giấy phép](https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge)](LICENSE)

**MineHost** là ứng dụng Android mã nguồn mở giúp bạn **tự tạo, vận hành và quản trị máy chủ Minecraft Java & Bedrock (Chơi chung PC + Điện thoại PE)** trực tiếp ngay trên điện thoại Android mà không cần thuê VPS.

👉 **[📥 TẢI NGAY APK MỚI NHẤT (minehost-2.7.4-arm64.apk)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)**

---

## ✨ Tính Năng Nổi Bật (v2.7.4)

### 1. ⚡ Đặc Trị Cho Điện Thoại RAM Ít (2GB – 4GB RAM) & Các Tính Năng Tốn 0 MB RAM
- **Chế độ Máy RAM Ít Siêu Nhẹ 1 Chạm:** Tự động tối ưu `view-distance=4`, `simulation-distance=3`, tắt đồng bộ ghi chunk nặng và ép cờ JVM tiết kiệm RAM.
- **Gói 5 Mod Sinh Tồn Siêu Nhẹ (`-45% RAM`):** Tự động tắt các mod nặng và chỉ cài 5 mod nhẹ nhất cho máy yếu: **FerriteCore** *(giảm ~45% RAM)*, **Lithium** *(tăng gấp đôi TPS)*, **Tree Harvester** *(chặt 1 gốc đổ cả cây)*, **Veinminer** *(đập 1 quặng ăn cả mạch)* và **Universal Graves** *(bia mộ giữ đồ khi chết)*.
- **Giới Hạn Biên Thế Giới (WorldBorder) 1 Chạm (`500` / `1.000` / `2.500` Ô):** Ngăn người chơi đi quá xa làm tràn RAM điện thoại.
- **Màn Hình Đen Treo Server (OLED Keep-Alive):** Tắt pixel màn hình AMOLED/OLED chỉ hiện đồng hồ & số người online giúp giảm nhiệt, tiết kiệm pin và chống Android tắt ngầm khi treo máy qua đêm.

### 2. 🤝 Chơi Chung PC (Java) + Điện Thoại (Bedrock PE) 1 Chạm
- **Tự động cài đặt GeyserMC + Floodgate + ViaVersion:** Điện thoại Minecraft PE (Bedrock) và máy tính PC (Java / PojavLauncher) vào chơi chung cùng 1 thế giới.
- **Kiểm tra tương thích trực tiếp:** Tự đối chiếu phiên bản Minecraft PE mới nhất vào được từng bản Java.
- **Tích hợp Playit.gg (TCP + UDP):** Hỗ trợ mở cả cổng TCP cho PC và cổng UDP riêng cho Điện thoại PE để bạn bè chơi từ xa qua 4G / khác mạng Wi-Fi, kèm **Thẻ Mời Bạn Bè 1 Chạm** sao chép tin nhắn đầy đủ IP + Cổng gửi qua Zalo / Messenger / Discord.

### 3. 🎮 Bộ Công Cụ Quyền Năng Admin 1 Chạm (Không Cần Nhớ Lệnh)
- **Bảng Luật Thế Giới Trực Tiếp:** Bật/tắt `Giữ đồ khi chết (keepInventory)`, `PvP`, `Nổ Creeper (mobGriefing)`, `Thời gian Ngày/Đêm` và `Thời tiết Nắng/Mưa` ngay lập tức mà không cần khởi động lại Server.
- **Thao tác Người Chơi 1 Chạm:** `❤️ Hồi Đầy Máu & Ăn`, `🎁 Tặng Starter Kit`, `🗡️ Sinh Tồn`, `🧱 Sáng Tạo`, `👻 Khán Giả`, `📍 Đưa về Spawn` và xem **Túi Đồ + EnderChest** trực quan bằng Tiếng Việt.
- **Tạo Tên Server Nhiều Màu Sắc (Visual MOTD Studio):** 4 mẫu tên máy chủ cầu vồng rực rỡ áp dụng trong 1 chạm.
- **Kho 6 Seed Đẹp & Kiểu Thế Giới Nhanh:** Sinh tồn đảo mây, Làng Hoa Anh Đào, Đảo Nấm Bất Tử, Pháo Đài Cổ Đại, Thế giới Siêu Phẳng.
- **Cài Addon `.mcpack` / `.mcaddon` / `.zip` 1 Chạm:** Tự động phân loại Behavior Pack và Resource Pack cho Bedrock.

### 4. 🔄 Cập Nhật Trực Tiếp Trong Ứng Dụng (OTA Update) & Chuẩn Store
- Kiểm tra, tải và cài đè phiên bản mới trực tiếp từ mục **Cài đặt → Kiểm tra cập nhật (OTA)** mà không mất dữ liệu Server.
- Tích hợp **Chẩn đoán phần cứng máy (Pre-flight Check)**, **Trình xem/sao chép Nhật ký lỗi (Crash Log Viewer)** và **Chính sách Quyền riêng tư (Zero Telemetry)**.

---

## 🚀 Hướng Dẫn Bắt Đầu Nhanh (3 Bước)

1. **Bước 1 — Cài môi trường chạy:** Mở ứng dụng → tại tab **Trang chủ**, bấm **Cài đặt môi trường chạy** (giữ kết nối Wi-Fi ổn định).
2. **Bước 2 — Tạo Server Core & Java:** Chọn nhân **Paper** (khuyên dùng nếu muốn chơi chung PC + PE) hoặc **Fabric** (nếu muốn cài Mod sinh tồn) → ứng dụng tự động tải Core và gợi ý phiên bản Java tương thích (Java 8 / 17 / 21 / 25).
3. **Bước 3 — Bật Server & Kết nối:**
   - Bấm **Bật Server** và xác nhận đồng ý [Minecraft EULA](https://www.minecraft.net/eula).
   - **Chơi cùng Wi-Fi:** Nhập địa chỉ IP LAN hiển thị ngay trên Trang chủ.
   - **Chơi khác mạng / 4G:** Bấm **Bật Đường Hầm Playit.gg** trên Trang chủ để lấy địa chỉ cho bạn bè ở xa.

---

## ⚖️ Bản Quyền, Quyền Riêng Tư & Giấy Phép

- **Giấy phép mã nguồn mở:** Phát hành theo giấy phép [GNU GPL-3.0-or-later](LICENSE), kế thừa nền tảng runtime từ dự án mã nguồn mở [Minexa](https://github.com/lovehutaocute/Minexa) ([NOTICE](NOTICE)).
- **Chính sách Quyền riêng tư:** Cam kết **Zero Telemetry** — không thu thập bất kỳ dữ liệu cá nhân nào. Xem chi tiết tại [PRIVACY.md](PRIVACY.md).
- **Tuyên bố miễn trừ thương hiệu Mojang / Microsoft:**
  > **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**  
  > *(Không phải sản phẩm Minecraft chính thức. Không được phê duyệt bởi hoặc liên kết với Mojang hay Microsoft.)*
