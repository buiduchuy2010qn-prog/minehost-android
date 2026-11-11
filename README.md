<div align="center">

# 🇻🇳 MINEHOST PRO — ANDROID MINECRAFT SERVER ENGINE
### **v3.1.0 (Build 69 • Official Community Edition)**

[![Release](https://img.shields.io/badge/RELEASE-v3.1.0%20(Build%2069)-10B981?style=for-the-badge&logo=android&logoColor=white)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![Engine](https://img.shields.io/badge/3--ENGINE-JAVA%20•%20RUST%20TURBO%20•%20NATIVE%20RUST-EA580C?style=for-the-badge&logo=rust&logoColor=white)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![Platform](https://img.shields.io/badge/ANDROID-8.0%2B%20(ARM64)-0EA5E9?style=for-the-badge&logo=linux&logoColor=white)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases)
[![Crossplay](https://img.shields.io/badge/CROSSPLAY-JAVA%20PC%20%2B%20BEDROCK%20PE-F59E0B?style=for-the-badge&logo=reverbnation&logoColor=white)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![OTA](https://img.shields.io/badge/OTA%20UPDATE-AUTO%20DETECT%20•%20AUTO%20CLEAN-6366F1?style=for-the-badge)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![License](https://img.shields.io/badge/LICENSE-GPLv3-3B82F6?style=for-the-badge)](LICENSE)

**Biến điện thoại Android thành máy chủ Minecraft chuyên nghiệp (Paper, Purpur, Fabric, Forge, NeoForge, Vanilla, Bedrock & Nhân Rust siêu nhẹ) — Hỗ trợ chơi chung PC & Điện thoại PE không cần thuê VPS hay mở Port Router.**

### 👉 [**📥 TẢI NGAY APK v3.1.0 MỚI NHẤT (`minehost-3.1.0-arm64.apk`)**](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/download/v3.1.0/minehost-3.1.0-arm64.apk)

</div>

---

## ✨ Đột Phá Mới Nhất Trên Bản `v3.1.0` (Build 69)

| Hạng mục nâng cấp | Công nghệ & Cơ chế hoạt động |
| :--- | :--- |
| **🎨 Giao Diện Trung Tâm Thế Hệ Mới + Sóng Đồ Thị 60s** | Thiết kế lại bảng điều khiển gọn thoáng hiện đại: lưới **4 ô thông số trực quan** (`TPS`, `RAM`, `Người chơi`, `Nhiệt độ & Pin`) tích hợp **sóng đồ thị thời gian thực 60 giây**, viên thuốc trạng thái sống `🟢 20.0 TPS • 37°C` trên thanh đỉnh mọi màn hình và **Menu `☰` có ô tìm kiếm nhanh công cụ** + 4 phím tắt 1 chạm. |
| **🦀 Bộ 3 Chế Độ Nhân Linh Hoạt (`Java Mod` • `Java + Rust Turbo` • `Rust Thuần`)** | Chuyển đổi tức thì trên 1 thanh gạt ngang: **① ☕ Java Nguyên Bản** (chơi Full Mod Forge/Fabric/NeoForge/Plugin), **② ⚡ Java + Rust Turbo** (chơi Mod nhẹ mượt, giảm khựng GC xuống `15ms`, khử trùng lặp chuỗi RAM), **③ 🦀 Nhân Rust Thuần** (Pumpkin ARM64 siêu nhẹ `~50MB RAM`, bật trong tích tắc cho máy yếu). |
| **☁️ Sao Lưu Map Lên Đám Mây Telegram 1 Chạm (`0 MB RAM`)** | Tự chạy `save-all`, nén thư mục thế giới thành `.zip` sạch an toàn (bỏ qua `session.lock` & tệp tạm), tự động cắt chia phần thông minh nếu Map `> 45 MB` và truyền luồng trực tiếp lên cuộc trò chuyện Telegram cá nhân — hoặc gõ `/backup` từ xa ngay trong Telegram. |
| **🏆 Bảng Điểm Scoreboard & Thanh BossBar Trong Game 1 Chạm** | Hiển thị bảng xếp hạng cạnh phải màn hình (*Chiến tích diệt quái, Số lần tử trận, Máu đồng đội dưới tên, Đua top đào Kim Cương*) và thanh thông báo **BossBar 6 màu** trên đỉnh màn hình cho toàn bộ người chơi mà **không tốn thêm 1 MB RAM hay cần cài Plugin/Mod**. Tự khôi phục mỗi lần mở Server. |
| **🔍 Radar Quét Điểm Nóng Gây Lag & Dọn Lag Bảo Vệ Dân Làng** | Quét trực tiếp tệp vùng `.mca` để chỉ đích danh tọa độ `(X, Z)` khu vực tập trung nhiều thực thể/máy farm gây tụt TPS nhất (kèm nút chép lệnh `/tp` 1 chạm). Tích hợp 2 chế độ dọn lag thông minh **cam kết bảo vệ 100% Dân làng, Thú cưng, Gia súc, Người sắt & Giá để giáp**. |
| **🗺️ 7 Pack Sinh Tồn Dựng Sẵn & Hộp Thoại Đổi Map An Toàn** | Dựng nhanh **OneBlock 10 Cấp, SkyBlock, Đại Dịch Zombie, Săn Rồng RPG, Parkour, Đảo Kho Báu, Lucky Island** thành thư mục thế giới riêng biệt — giữ nguyên 100% mọi Map cũ và tích hợp trình **Dọn Rác Bộ Nhớ 1 Chạm** giải phóng dung lượng đệm. |
| **🌐 Web Remote `:8585` Bảo Mật PIN & Bot Telegram 2 Chiều** | Bảng điều khiển từ xa qua trình duyệt LAN/Wi-Fi có khóa mã PIN an toàn + Bot Telegram 2 chiều hỗ trợ `/status`, `/ip`, `/list`, `/backup`, `/clearlag`, `/day`, `/save`, `/say` kèm **Ảnh Thẻ Trạng Thái & Thẻ Mời HD (`1080×640` PNG)**. |

---

## ⚡ Hệ Sinh Thái Tính Năng Toàn Diện

### 🌐 1. Kết Nối Đa Nền Tảng (PC Java + Điện Thoại Bedrock/PE)
- **Đường hầm mạng tích hợp sẵn:** Hỗ trợ **Playit.gg**, **FRP** và **Bore** giúp bạn bè ở khác mạng Wi-Fi / 4G vào chơi ngay mà không cần mở Port modem.
- **Crossplay GeyserMC + Floodgate + ViaVersion 1 chạm:** Tự động cấu hình đồng thời cổng TCP (cho PC Java) và cổng UDP (cho Điện thoại Bedrock/PE), hỗ trợ đồng bộ Skin cho cả tài khoản Cracked.
- **Trình tạo Thẻ Mời Server HD (`1080×640`):** Xuất ảnh Banner mời vào Server hiển thị rõ IP PC & IP/Port PE để chia sẻ nhanh qua Discord, Telegram, Zalo, Messenger.

### 🔋 2. Tối Ưu Hóa Phần Cứng & Tự Động Bảo Vệ Thế Giới
- **Self-Healing Core & Port Guard:** Tự động sửa lỗi bộ đệm `.fabric/remappedJars` bị ngắt dở, mở khóa `session.lock` và tự chuyển sang cổng trống nếu cổng `25565` bị chiếm.
- **Thermal & Battery Guard:** Tự động cảnh báo trong game, thực thi `save-all` kép và tắt máy chủ an toàn khi pin xuống `≤ 10%` hoặc nhiệt độ chạm ngưỡng `≥ 45.0°C`.
- **Smart Mod Doctor (1-Tap Auto-Fix):** Tự động quét `mods/`, tải bù thư viện phụ thuộc còn thiếu (`Fabric API`, `Cloth Config`, `Architectury`...) và cách ly Mod Client-only / lệch phiên bản.
- **Turbo Boot & Instant Stop Engine:** Tăng tốc nạp Core và mở/đóng Server gấp 2–3 lần kèm âm báo **"Minecraft Level Up"** khi máy chủ sẵn sàng vào game.

### 👑 3. Quản Trị Máy Chủ & Sự Kiện In-Game (`0 MB RAM Overhead`)
- **Quản lý người chơi & Túi đồ NBT trực quan:** Cấp/gỡ OP (đã gỡ bỏ nút `@a` mặc định để chống cấp nhầm), đổi chế độ chơi, xem/chỉnh sửa trực tiếp 41 ô túi đồ NBT, tặng Starter Kit, Whitelist & Ban.
- **OP Guard cho Server Cracked:** Khóa bảo vệ quyền Admin trước nguy cơ giả mạo tên chủ Server.
- **Xưởng Danh Hiệu VIP & Sự Kiện Mini:** Gắn prefix màu sắc (`[👑 Chủ Server]`, `[⚔️ Chiến Thần]`, `[💎 Đại Gia]`), thả Rương Tiếp Tế, Mưa Kinh Nghiệm, Siêu Tốc Độ Đào và Radar tìm công trình (`/locate`).

---

## 📦 Thông Số Kỹ Thuật & Cập Nhật OTA

- **Định danh gói (`applicationId`):** `vn.minehost.pro`
- **Phiên bản mới nhất:** `v3.1.0` (`versionCode = 69`)
- **Kiến trúc hỗ trợ:** `arm64-v8a` (Android 8.0 / API 26 trở lên)
- **Môi trường Runtime:** OpenJDK `8`, `17`, `21`, `25` (Termux glibc/proot & FCL Native Runtime) + Native ARM64 Rust Engine
- **Cập nhật OTA Tự Động:** Tự phát hiện bản mới ngay khi mở ứng dụng, xác thực chữ ký số gốc (`CN=MineHost Android`), cài đè trực tiếp **giữ nguyên 100% Thế giới, Mod, Plugin & Cài đặt**, và tự động dọn sạch tệp `.apk` cũ sau khi nâng cấp.

---

## ⚖️ Giấy Phép & Bản Quyền

- Phát hành theo giấy phép mã nguồn mở **[GNU GPL-3.0-or-later](LICENSE)**, kế thừa nền tảng runtime từ dự án [Minexa](https://github.com/lovehutaocute/Minexa) ([NOTICE](NOTICE)).
- **Quyền riêng tư ([PRIVACY.md](PRIVACY.md)):** Cam kết **Zero Telemetry** — không thu thập dữ liệu cá nhân, không chứa quảng cáo hay trình theo dõi.
- **Tuyên bố miễn trừ Mojang / Microsoft:**
  > **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
