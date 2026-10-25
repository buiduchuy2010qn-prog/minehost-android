<div align="center">

# 🇻🇳 MINEHOST PRO — ANDROID MINECRAFT SERVER ENGINE
### **v2.8.7 (Build 54 • Official Community Edition)**

[![Release](https://img.shields.io/badge/RELEASE-v2.8.7%20(Build%2054)-10B981?style=for-the-badge&logo=android&logoColor=white)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![Platform](https://img.shields.io/badge/ANDROID-8.0%2B%20(ARM64)-0EA5E9?style=for-the-badge&logo=linux&logoColor=white)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases)
[![Crossplay](https://img.shields.io/badge/CROSSPLAY-JAVA%20PC%20%2B%20BEDROCK%20PE-F59E0B?style=for-the-badge&logo=reverbnation&logoColor=white)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![OTA](https://img.shields.io/badge/OTA%20UPDATE-ZERO%20CACHE%20•%20AUTO%20CLEAN-6366F1?style=for-the-badge)](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)
[![License](https://img.shields.io/badge/LICENSE-GPLv3-3B82F6?style=for-the-badge)](LICENSE)

**Biến điện thoại Android thành máy chủ Minecraft chuyên nghiệp (Paper, Purpur, Fabric, Forge, NeoForge, Vanilla, Bedrock) — Hỗ trợ chơi chung PC & Điện thoại PE không cần thuê VPS hay mở Port Router.**

### 👉 [**📥 TẢI NGAY APK v2.8.7 MỚI NHẤT (`minehost-2.8.7-arm64.apk`)**](https://github.com/buiduchuy2010qn-prog/minehost-android/releases/latest)

</div>

---

## 🚀 Điểm Nhấn Đột Phá Trên Bản `v2.8.7` (Build 54)

| Tính năng | Công nghệ & Cơ chế hoạt động |
| :--- | :--- |
| **🔊 Level-Up Audio & Live Running Pulse** | Tự động phát giai điệu hợp âm **"Minecraft Level Up" (PCM 16-bit 44.1kHz + Rung phản hồi)** ngay khi Server mở thành công (`StartupPhase.Ready`), kèm hiệu ứng viền nhịp thở phát sáng (`InfiniteTransition`) và thanh tiến trình trực tiếp `0% → 100%` cho mọi tác vụ tải xuống/cài đặt. |
| **🗺️ 1-Tap Playable Worlds (`OneBlock`, `SkyBlock`, `Parkour`, `Lucky Island`)** | Tạo nhanh thế giới mới 1 chạm kèm Datapack chuẩn kép (`function` + `functions` cho MC `1.20.x–1.21+`), tự dựng đảo khởi đầu, rương sinh tồn và giữ nguyên 100% các thế giới cũ. |
| **🎙️ Simple Voice Chat 1-Tap (`UDP 24454`)** | Tự động dò đúng phiên bản Minecraft & Loader (`Fabric` / `Forge` / `NeoForge` / `Paper`) trên Modrinth, tải trực tiếp có thanh phần trăm và tự sinh cấu hình `voicechat-server.properties`. |
| **🎨 Resource Pack Studio & Auto SHA-1** | Kho gói tài nguyên 1 chạm (**Việt Hóa Giao Diện**, **Quặng Phát Sáng**, **Dark UI**, **Cỏ Đẹp**) hoặc dán URL `.zip` tùy chọn — tự động tải luồng để băm mã kiểm tra `resource-pack-sha1` chuẩn 100%. |
| **🛡️ Anti-Grief Shield 2.0 & Auto-ClearLag 15m** | Lưu bền vững cấu hình Luật Thế Giới (`keepInventory`, `mobGriefing`, `doFireTick`, `playersSleepingPercentage`) để tự động áp lại mỗi lần mở Server; dọn rác định kỳ 15 phút bảo vệ tuyệt đối thú cưng đã đặt tên (`name=!`), dân làng và Boss. |
| **⚡ Turbo Boot & Instant Stop Engine** | Tăng tốc nạp Core (62%) và mở/đóng Server gấp 2–3 lần: bộ nhớ đệm xác thực môi trường `0ms`, bỏ chạy lặp `fc-cache -f`, tắt kiểm tra phiên bản Mod trực tuyến (`fml.toml`), ghi chunk bất đồng bộ (`sync-chunk-writes=false`) và giải phóng nút **Dừng máy chủ** tức thì (`< 0.3s`). |
| **🛠️ Smart Mod Doctor (1-Tap Auto-Fix)** | Tự động mổ xẻ `fabric.mod.json` / `mods.toml` của toàn bộ tệp `.jar` trong `mods/`: tự tải bù thư viện còn thiếu (`Fabric API`, `Cloth Config`, `Architectury`...), tự thay thế hoặc cách ly (`.disabled`) các Mod lệch phiên bản / Client-only / trùng ID. |
| **🤖 Discord Webhook Pro & 2-Way Game Chat** | Tự động bắn IP Server + thông báo khi người chơi vào/rời Server lên kênh Discord nhóm và trò chuyện trực tiếp 2 chiều giữa Chủ Host với người chơi trong game. |

---

## ⚡ Hệ Sinh Thái Tính Năng Toàn Diện

### 🌐 1. Kết Nối Đa Nền Tảng (PC + Điện Thoại PE)
- **Đường hầm mạng tích hợp sẵn:** Hỗ trợ **Playit.gg**, **FRP** và **Bore** giúp bạn bè ở khác mạng Wi-Fi / 4G vào chơi ngay mà không cần mở Port modem.
- **Crossplay GeyserMC + Floodgate + ViaVersion 1 chạm:** Tự động cấu hình đồng thời cổng TCP (cho PC Java) và cổng UDP (cho Điện thoại Bedrock/PE).
- **Trình tạo Thẻ Mời Server HD (`1080×640`):** Xuất ảnh Banner mời vào Server hiển thị rõ IP PC & IP/Port PE để chia sẻ qua Discord, Zalo, Messenger.

### 🔋 2. Tối Ưu Hóa Phần Cứng & Bảo Vệ Dữ Liệu Thế Giới
- **Thermal & Battery Guard:** Tự động phát cảnh báo trong game, thực thi `save-all` kép và tắt máy chủ an toàn khi pin xuống `≤ 10%` hoặc nhiệt độ máy chạm ngưỡng `≥ 45.0°C`.
- **Auto-Sleep Idle Mode:** Tự động lưu thế giới và đưa máy chủ về chế độ nghỉ khi không có người chơi trực tuyến sau `15 / 30 / 60 phút`.
- **1-Tap Low-RAM Optimizer:** Tự động tinh chỉnh `view-distance`, `simulation-distance`, Aikar's Flags và dọn rác bộ nhớ RAM trước khi khởi chạy JVM.

### 👑 3. Quản Trị Máy Chủ & Sự Kiện In-Game (0 MB RAM Overhead)
- **Quản lý người chơi 1 chạm:** Cấp/gỡ OP, đổi chế độ chơi (`Survival` / `Creative` / `Spectator`), tặng Starter Kit, quản lý túi đồ NBT trực quan, Whitelist & Ban.
- **OP Guard cho Server Cracked:** Tự động khóa quyền Admin khi tài khoản chưa được chủ máy xác thực trên ứng dụng.
- **Xưởng Danh Hiệu VIP & Sự Kiện Mini:** Gắn prefix màu sắc (`[👑 Chủ Server]`, `[⚔️ Chiến Thần]`, `[💎 Đại Gia]`), thả Rương Tiếp Tế, Mưa Kinh Nghiệm, Siêu Tốc Độ Đào và Radar tìm công trình (`/locate` + dịch chuyển cả nhóm).

---

## 📦 Thông Số Kỹ Thuật & Cài Đặt

- **Định danh gói (`applicationId`):** `vn.minehost.pro`
- **Kiến trúc hỗ trợ:** `arm64-v8a` (Android 8.0 / API 26 trở lên)
- **Môi trường Java hỗ trợ:** OpenJDK `8`, `17`, `21`, `25` (Termux glibc/proot & FCL Native Runtime)
- **Cơ chế cập nhật OTA:** Kiểm tra trực tiếp từ GitHub API + Manifest Raw chống lưu đệm (`no-cache`), hỗ trợ cài đè giữ nguyên 100% dữ liệu Thế giới/Mod và tự động dọn sạch tệp `.apk` sau khi nâng cấp.

---

## ⚖️ Giấy Phép & Bản Quyền

- Phát hành theo giấy phép mã nguồn mở **[GNU GPL-3.0-or-later](LICENSE)**, kế thừa nền tảng runtime từ dự án [Minexa](https://github.com/lovehutaocute/Minexa) ([NOTICE](NOTICE)).
- **Quyền riêng tư ([PRIVACY.md](PRIVACY.md)):** Cam kết **Zero Telemetry** — không thu thập dữ liệu cá nhân, không chứa quảng cáo hay trình theo dõi.
- **Tuyên bố miễn trừ Mojang / Microsoft:**
  > **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
