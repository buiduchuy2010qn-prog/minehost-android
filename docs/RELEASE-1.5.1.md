MineHost 1.5.1 — cấu hình nhẹ cho Android ARM64 (Android 8 trở lên).

- Thống nhất mặc định Java: tầm nhìn **6 chunk**, mô phỏng **4 chunk**, không còn gợi ý 16–24 chunk.
- Server mới dùng giới hạn người chơi thận trọng theo bộ nhớ/CPU; RAM tự động vẫn có thể bật tại Tổng quan.
- Server đã có cấu hình riêng không bị tự đổi. Muốn chuyển về 6/4: dừng server → Tổng quan → Tối ưu theo điện thoại → Áp dụng cấu hình nhẹ. Có bản sao server.properties trước khi đổi.
- Đường khởi động qua lịch/tự khởi động lại cũng bổ sung 6/4 nếu thiếu khóa cấu hình. Paper/Spigot có thể ghi đè theo world trong spigot.yml; không tự viết lại YAML nhập từ ngoài.
- Kho plugin/mod dùng danh sách lazy có vùng cuộn riêng. Ảnh biểu tượng: tối đa 2 yêu cầu đồng thời, 2 MiB mỗi ảnh, cache RAM 2 MiB, giải mã ảnh nhỏ; lỗi tải dùng biểu tượng dự phòng.
- Tổng quan/Console/Kho plugin-mod tạm dừng thu thập trạng thái UI khi Activity không hiển thị; không dừng server hoặc tunnel.
- Lưu cấu hình thành công rồi mới khởi động lại từ Console, tránh chạy trước khi ghi xong.
- Giữ các tính năng 1.5: nhập/chuyển mod, địa chỉ kết nối và lịch sử người chơi.

Tải **MineHost-1.5.1-arm64.apk** bên dưới. Repo riêng tư: cần đăng nhập GitHub có quyền truy cập.

Cài đè bản dùng khóa ký cố định từ alpha.2 trở lên, cùng applicationId vn.minehost.android. Hãy dừng server và sao lưu trước khi cập nhật. Alpha.1 có khóa cũ khác: xem MIGRATION.md; không gỡ app nếu chưa xuất và kiểm tra bản sao ngoài dữ liệu app.

GitHub Actions kiểm tra unit test, bản release R8 không debuggable và chữ ký cố định; xem VERIFICATION.json và SOURCE-COMMIT.txt. Lint kế thừa còn lỗi. **Chưa thử trên Galaxy A23/Redmi 15C, chưa đo FPS/TPS hay nhiệt độ; không cam kết hết lag hoặc 20 TPS với mọi mod/world.**

GPL-3.0-or-later, kế thừa Minexa. Mã nguồn tương ứng tại tag này.
