# MineHost Android

Ứng dụng Android dự kiến dùng để chạy server Minecraft Java ngay trên điện thoại, không cần root. Hỗ trợ Bedrock chơi chung qua Geyser + Floodgate là giai đoạn tiếp theo.

> Trạng thái: lập kế hoạch. Chưa có mã ứng dụng, workflow build hoặc APK; chưa kiểm thử trên điện thoại thật.

## Phạm vi đã thống nhất

- Java là bản chính, dùng Paper làm phần mềm server.
- Java và Bedrock có thể cùng chơi một thế giới Java qua Geyser + Floodgate; không phải hai server độc lập hay server Bedrock gốc.
- Máy thử nghiệm của chủ dự án có 8 GB RAM. Mẫu máy, CPU, phiên bản Android và phiên bản Minecraft chưa xác định.
- Không cần thuê VPS để chạy game: điện thoại là máy host. Chơi ngoài mạng nội bộ cần mở cổng hoặc tunnel phù hợp.
- Không clone repo, ghi source hoặc build APK trên máy tính của chủ dự án. Chỉnh sửa qua GitHub; khi có ứng dụng, build bằng GitHub Actions.
- Điện thoại chạy server vẫn phải lưu ứng dụng, Java runtime, world và bản sao lưu.

## Kế hoạch MVP

1. **Kiểm chứng runtime trên máy thật**
   - Xác nhận ARM64, CPU, Android, dung lượng trống và phiên bản game.
   - Chọn Java runtime tương thích với phiên bản Paper và Android; thử khởi động không root.
   - Kiểm tra giới hạn tiến trình nền, target SDK và đóng gói runtime trước khi chốt APK độc lập.
   - Nghiệm thu: một máy khác vào được qua LAN; dừng đúng cách, khởi động lại và world không mất dữ liệu.
2. **Ứng dụng Java cơ bản**
   - Giao diện tiếng Việt; cài server, bật/tắt, log, giới hạn RAM, số người và whitelist.
   - Hiển thị điều khoản Minecraft để người dùng tự đọc/chấp nhận khi cần.
   - Dừng mềm và lưu world trước khi kết thúc tiến trình.
3. **Chơi chung Java + Bedrock**
   - Tùy chọn cài Geyser/Floodgate với bộ phiên bản tương thích.
   - Hiển thị địa chỉ kết nối riêng cho từng loại client.
   - Kiểm tra xác thực, whitelist, tên và danh tính người chơi giữa hai bản.
   - Nghiệm thu: một client Java và một client Bedrock cùng chơi trên một world.
4. **Kết nối qua Internet**
   - Đánh giá mở cổng và tunnel; kiểm thử NAT/CGNAT, TCP/UDP theo transport Geyser thực tế.
   - Không mặc định tunnel miễn phí hoặc tương thích với mọi phiên bản Bedrock.
   - Không công khai console, RCON hoặc endpoint quản trị.
   - Nghiệm thu: người chơi từ mạng khác kết nối thành công.
5. **Ổn định và phát hành thử**
   - Sao lưu/khôi phục world, xuất log, giới hạn log, báo lỗi và cảnh báo thiếu dung lượng.
   - Thử tắt màn hình, chuyển mạng, nhiệt độ cao, thiếu RAM và khởi động lại.
   - Mục tiêu thử 3–5 người trong 2 giờ; ghi RAM, TPS và sự cố thay vì cam kết trước hiệu năng.
   - Build APK trên GitHub Actions; lưu artifact có thời hạn ngắn, không tải xuống máy tính nếu chưa được yêu cầu.

## Cấu hình thử ban đầu

- Heap server: thử 2 GB, chỉ tăng lên 3 GB sau khi đo bộ nhớ còn trống.
- Khoảng nhìn: 4–6 chunk.
- Một server và một world; chưa hỗ trợ modpack nặng trong MVP.
- Đây là mục tiêu kiểm thử, không phải bảo đảm chạy mượt hoặc luôn online 24/7.
- Nếu vừa host vừa chơi trên cùng điện thoại, cần kiểm thử lại tải và nhiệt độ.

## Nền tảng và đầu mối tham khảo

- [PaperMC](https://docs.papermc.io/paper/getting-started/): server Java và yêu cầu runtime theo phiên bản.
- [Geyser Setup](https://geysermc.org/wiki/geyser/setup/) và [Floodgate](https://geysermc.org/wiki/floodgate/): chơi chung Java/Bedrock.
- [Giới hạn Geyser](https://geysermc.org/wiki/geyser/current-limitations/): các khác biệt cần công bố khi thử nghiệm.
- [Minexa — lovehutaocute](https://github.com/lovehutaocute/Minexa): ứng viên tham khảo cho app host Android; cần kiểm thử và rà giấy phép trước khi tái sử dụng mã.
- [MSM — sizwinz](https://github.com/sizwinz/MSM-minecraft-server-manager-termux): tham khảo quản lý server trên Termux; không phải giao diện APK hoàn chỉnh.
- [Maxence Simon](https://www.malt.com/profile/maxencesimon): đầu mối freelance Java/Kotlin/Minecraft; chưa xác minh kinh nghiệm runtime Android hoặc khả năng nhận việc.

Chưa liên hệ hay thuê developer nào. Chưa sao chép mã nguồn từ dự án bên thứ ba. Repo được tạo private; không công khai hoặc phát sinh chi phí dịch vụ khi chưa được chủ dự án đồng ý.