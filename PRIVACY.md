# Chính Sách Quyền Riêng Tư (Privacy Policy) — MineHost Android

**Cập nhật lần cuối:** 24/10/2026  
**Ứng dụng:** MineHost (`vn.minehost.android`)  
**Kho mã nguồn chính thức:** [https://github.com/buiduchuy2010qn-prog/minehost-android](https://github.com/buiduchuy2010qn-prog/minehost-android)

---

## 1. Cam Kết Không Thu Thập Dữ Liệu (Zero Telemetry)
- **MineHost hoàn toàn không thu thập, không lưu trữ và không truyền tải** bất kỳ thông tin cá nhân, danh bạ, tin nhắn, vị trí địa lý hay mã định danh thiết bị (`Android ID`, `IMEI`, `Advertising ID`) nào của người dùng về máy chủ của nhà phát triển hoặc bên thứ ba.
- Module thống kê người dùng của nhánh thượng nguồn (`UsageTracker`) đã được **vô hiệu hóa hoàn toàn** trong mã nguồn của MineHost.
- Toàn bộ thế giới Minecraft (`world`), cấu hình máy chủ (`server.properties`), nhật ký hoạt động (`logs`) và tệp sao lưu (`backups`) được lưu trữ **cục bộ 100% trên thiết bị Android của bạn**.

---

## 2. Mục Đích Sử Dụng Các Quyền Của Thiết Bị (Permissions)

| Quyền hạn (Permission) | Mục đích sử dụng thực tế trong MineHost |
| :--- | :--- |
| `INTERNET` & `ACCESS_NETWORK_STATE` | Tải nhân máy chủ Minecraft chính thức (PaperMC, Fabric, Purpur, Vanilla, Bedrock), tải Java Runtime, tải Plugin/Mod từ kho công khai (Modrinth, Hangar, Spiget, GeyserMC), kiểm tra bản cập nhật ứng dụng từ GitHub Releases và kết nối đường hầm mạng (Playit.gg, FRP, Bore, STUN). |
| `FOREGROUND_SERVICE` & `WAKE_LOCK` | Duy trì tiến trình máy chủ Minecraft hoạt động ổn định dưới nền khi bạn tắt màn hình hoặc chuyển sang ứng dụng Minecraft để vào chơi. |
| `POST_NOTIFICATIONS` | Hiển thị thanh trạng thái máy chủ đang chạy và thông báo khi có bản cập nhật hoặc sự kiện máy chủ. |
| `MANAGE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` | Cho phép xuất/nhập bản sao lưu thế giới (`.zip`), nhập thế giới có sẵn và cài đặt tệp `.jar` / `.mcpack` / `.mcaddon` do chính người dùng chọn từ bộ nhớ máy. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Tùy chọn giúp ngăn hệ điều hành Android tự động đóng máy chủ khi đang treo máy. |
| `REQUEST_INSTALL_PACKAGES` | Chỉ sử dụng khi người dùng chủ động bấm "Cập nhật ứng dụng" trong mục Cài đặt để cài bản APK mới tải từ GitHub Releases chính thức (có kiểm tra đối chiếu chữ ký số SHA-256). |

---

## 3. Dịch Vụ Bên Thứ Ba Do Người Dùng Chủ Động Kết Nối
Khi bạn chủ động bấm tải tài nguyên hoặc bật đường hầm mạng, thiết bị của bạn sẽ kết nối trực tiếp tới các dịch vụ công khai tương ứng:
- **Mojang / PaperMC / FabricMC / PurpurMC:** Tải nhân máy chủ Minecraft.
- **Modrinth / Hangar / Spiget / GeyserMC:** Tìm kiếm và tải Plugin / Mod / Geyser / Floodgate / ViaVersion.
- **Playit.gg:** Tạo đường hầm TCP (Java) và UDP (Bedrock) để bạn bè kết nối từ xa qua Internet.
- **GitHub (`api.github.com` / `github.com/buiduchuy2010qn-prog/minehost-android`):** Kiểm tra và tải bản cập nhật ứng dụng mới nhất.

---

## 4. Tuyên Bố Bản Quyền & Miễn Trừ Trách Nhiệm (Mojang / Microsoft Disclaimer)

> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**  
> *(MineHost không phải là sản phẩm Minecraft chính thức, không được phê duyệt bởi hoặc liên kết với Mojang AB hay Microsoft Corporation.)*

- Người dùng phải tự đọc và đồng ý với [Thỏa thuận Cấp phép Người dùng Cuối của Minecraft (Minecraft EULA)](https://www.minecraft.net/eula) trước khi khởi chạy máy chủ. MineHost không tự động đồng ý EULA thay cho người dùng.

---

## 5. Giấy Phép Mã Nguồn Mở & Liên Hệ
- Phát hành theo giấy phép **GNU General Public License v3.0 or later (GPL-3.0-or-later)**.
- Mọi đóng góp, báo lỗi hoặc câu hỏi về quyền riêng tư vui lòng gửi tại: [GitHub Issues — buiduchuy2010qn-prog/minehost-android](https://github.com/buiduchuy2010qn-prog/minehost-android/issues).
