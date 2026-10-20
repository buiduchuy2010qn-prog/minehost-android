# Chuyển alpha.1 sang alpha.2

Alpha.1 dùng khóa ký tạm không được lưu. Android yêu cầu đúng khóa cũ để cài đè nên lần chuyển này cần sao lưu và cài lại.

1. Dừng server, đợi lưu hoàn tất.
2. App cũ → Quản lý → Thế giới → xuất từng world thành ZIP (màn hình cũ có thể ghi 世界管理 và 导出). Lưu vào Download hoặc nơi ngoài dữ liệu app.
3. Để giữ plugin/mod/cấu hình, tạo thêm bản sao lưu toàn bộ server và xuất ra ngoài app. ZIP world không chứa mọi cấu hình hay plugin/mod.
4. Kiểm tra ZIP mở được, có level.dat/region và các chiều cần giữ. Nên thử khôi phục bản sao ở nơi khác. Nếu chưa chắc bản sao an toàn, giữ app cũ.
5. Ghi lại core, phiên bản Minecraft, Java và loader. Thiết lập riêng của app có thể cần chọn lại.
6. Sau khi có bản sao an toàn, gỡ alpha.1 rồi cài alpha.2, cài môi trường, tạo/chọn server tương thích.
7. Tổng quan → Nhập thế giới có sẵn → ZIP → Dùng world này. Với ZIP toàn server, dùng nhập/khôi phục server, không dùng mục nhập world.
8. Bật thử và kiểm tra Overworld, Nether, End, người chơi và add-on trước khi bỏ bản sao cũ.

Gỡ app xóa dữ liệu trong vùng lưu trữ riêng của app. Cuộc chat không thể xác minh sao lưu trên điện thoại.

## Cập nhật từ alpha.2 trở đi

Giữ applicationId vn.minehost.android, cùng khóa ký và tăng versionCode. Khóa cố định được lưu tại Actions Secret MINEHOST_SIGNING_BUNDLE; chứng thư công khai tại signing/minehost-certificate.pem. Workflow thiếu khóa sẽ dừng, không sinh khóa mới.

Các bản sau dùng khóa này để hỗ trợ cài đè, giữ dữ liệu app. Vẫn cần sao lưu trước cập nhật và kiểm thử thiết bị.
