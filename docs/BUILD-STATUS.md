# Kết quả build alpha đầu tiên

- Ngày: 2026-10-20
- Source commit của APK: 0cefe7fde3cfa58de3373370aa52ca03dc71896f
- [Workflow thành công](https://github.com/duchilocket-oss/minehost-android/actions/runs/39385028289)
- [APK thử nghiệm ARM64, ZIP khoảng 26 MB](https://github.com/duchilocket-oss/minehost-android/actions/runs/39385028289/artifacts/12247671729)
- [Báo cáo kiểm thử và lint](https://github.com/duchilocket-oss/minehost-android/actions/runs/39385028289/artifacts/12247482056)

Repo riêng tư: cần đăng nhập tài khoản có quyền truy cập.
Artifact APK giữ 14 ngày; báo cáo giữ 7 ngày. Đây không phải GitHub Release.

## Đã xác minh

- assembleDebug thành công.
- 625 kiểm thử trong 66 test suite: 0 thất bại, 0 lỗi, 0 bỏ qua.
- MineHostSafetyTest: 10/10 đạt.
- Không tải APK hoặc clone mã nguồn vào máy tính người dùng.
- Báo cáo được đọc trực tiếp qua API trong bộ nhớ.

## Chưa đạt tiêu chuẩn phát hành

Lint chạy xong nhưng cấu hình upstream không chặn build khi có lỗi.
**Không được mô tả build này là “lint sạch” hoặc “đã kiểm thử end-to-end”.**

Lint: 1572 lỗi, 218 cảnh báo và 8 thông tin.

| Nhóm lỗi | Số lượng |
|---|---:|
| Thiếu bản dịch | 1511 |
| Flow operator trong Compose | 54 |
| Định dạng chuỗi | 3 |
| ExactAlarm | 1 |
| API Android chưa được bảo vệ đầy đủ | 1 |
| Quyền chỉ dành cho hệ thống | 1 |
| produceState chưa gán value | 1 |

Các lỗi ngoài bản dịch tập trung ở màn hình/runtime kế thừa.
Không có lint Error/Fatal trong MineHostScreens.kt, AddonSafety.kt, EulaConsent.kt hoặc McApp.kt ở báo cáo này.
Điều này không chứng minh các tệp đó không có bug.

Chưa chạy app trên Android ARM64 thật, chưa có ảnh chụp giao diện từ thiết bị,
chưa xác minh Java/bootstrap, mở server Minecraft, LAN, tunnel, Geyser hoặc khôi phục world end-to-end.
STUN trên Android cũ cần đặc biệt kiểm tra API tương thích.

Khóa debug tạm thời: chỉ cài thử với world mới.
Đọc [checklist thiết bị](DEVICE-TESTING.md) và xuất backup trước khi gỡ/cài APK khác.
