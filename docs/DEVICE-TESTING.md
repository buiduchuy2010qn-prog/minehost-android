# Kiểm thử trên điện thoại trước khi dùng thật

Bản alpha chưa được chạy trên thiết bị thật trong quá trình tạo repo.
Chỉ dùng một world mới, không dùng dữ liệu quan trọng cho lần thử đầu.

## Thông tin cần ghi

- Model điện thoại / SoC:
- Android / ROM:
- RAM: 8 GB (theo người dùng); dung lượng trống:
- SHA commit trong SOURCE-COMMIT.txt của artifact:
- Core, phiên bản Minecraft và Java:

## Chạy tối thiểu

1. Cài APK ARM64; kiểm tra màn hình có 5 tab, chữ không bị cắt ở cỡ chữ hệ thống lớn.
2. Cho bootstrap hoàn tất trên Wi-Fi. Kiểm tra khi mất mạng có báo lỗi/thử lại.
3. Tải Paper, cài Java đúng phiên bản. Chọn heap 2048 MB.
4. Bấm Bật server: từ chối EULA phải không chạy; đồng ý chỉ áp dụng server được chọn.
5. Chờ trạng thái sẵn sàng và log Done. Không coi nút “đang bật” là đã chạy thành công.
6. Một máy Java khác cùng Wi-Fi vào được bằng IP:port; kiểm tra tên trong mục Người chơi.
7. Thử whitelist, OP rồi bỏ OP với tài khoản thử nghiệm. Không mở console/RCON public.
8. Dừng an toàn, bật lại và kiểm tra world được lưu. Thử khởi động lại khi có người đang chơi.
9. Xuất backup, sửa world thử nghiệm, dừng server rồi restore. Xác minh dữ liệu khôi phục đúng.

## Plugin và mod

- Paper: nhập một plugin tin cậy tương thích; log nhận đúng plugin.
- Fabric: thử một mod server tương thích, cùng các dependency yêu cầu.
- JAR giả/HTML/OptiFine/client-only Fabric phải bị từ chối; JAR đang cài không bị mất.
- Chọn sai phiên bản Modrinth phải báo lỗi, không tải bản mới nhất tùy ý.
- Khi có dependency bắt buộc: alpha yêu cầu nhập bộ JAR thủ công, chưa tự giải dependency.
- Nếu cài lỗi, kiểm tra mục Tệp và log; không liên tục bật lại để tránh hỏng dữ liệu.

## Chạy nền và kết nối xa

- Tắt màn hình 10–15 phút, kiểm tra máy khác vẫn kết nối; theo dõi nhiệt độ/pin.
- Thử đổi Wi-Fi và mất mạng; địa chỉ LAN phải cập nhật, không hiển thị địa chỉ mẫu.
- Muốn bạn khác mạng vào: cấu hình port/tunnel; kiểm tra từ mạng khác thật.
- Geyser/Floodgate: kiểm tra đúng phiên bản plugin, cấu hình auth và cổng theo tài liệu hiện hành.
- Không tắt online-mode cho Java để “sửa” lỗi kết nối; không chia sẻ key.pem.

## Nâng cấp APK

Khóa ký CI tạm thời có thể khiến Android không cho cài đè build mới.
**Xuất backup ra ngoài dữ liệu app trước khi gỡ cài đặt**, sau đó mới cài build mới.
Khóa release ổn định và kiểm thử nâng cấp chưa hoàn tất.

Nếu báo lỗi, gửi log đã che token, địa chỉ riêng tư và dữ liệu nhạy cảm; ghi rõ model máy, Android, Java, core và SHA build.
