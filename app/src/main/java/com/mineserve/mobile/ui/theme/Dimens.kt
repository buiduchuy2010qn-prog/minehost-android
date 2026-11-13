package com.mineserve.mobile.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Hệ thống khoảng cách chuẩn điện thoại Pure Horizon Glass (v3.2.0).
 */
object McSpacing {
    /** Lề trái/phải an toàn toàn trang. */
    val Page = 16.dp

    /** Khoảng cách dọc giữa các thẻ kính. */
    val CardGap = 10.dp

    /** Khoảng cách dọc giữa các thẻ kính gọn (compact). */
    val CardGapCompact = 6.dp

    /** Đệm bên trong thẻ kính tiêu chuẩn. */
    val CardInner = 16.dp

    /** Đệm bên trong thẻ kính gọn. */
    val CardInnerCompact = 12.dp

    /** Khoảng cách giữa các phân vùng trong thẻ. */
    val Section = 16.dp

    /** Khoảng cách giữa các phần tử trên cùng một hàng. */
    val Row = 8.dp

    /** Khoảng cách giữa tiêu đề thẻ và nội dung. */
    val CardHeader = 12.dp

    /** Khoảng cách giữa tiêu đề thẻ gọn và nội dung. */
    val CardHeaderCompact = 8.dp
}

/**
 * Hệ thống bo góc Pure Horizon Glass lấy cảm hứng từ BlockWay PC cho điện thoại.
 */
object McShape {
    /** Thẻ kính chính (McCard, HostCard, khung trang). */
    val Card = RoundedCornerShape(20.dp)

    /** Nút bấm chính, ô nhập liệu và điều khiển. */
    val Control = RoundedCornerShape(14.dp)

    /** Nút thao tác gọn trong hàng danh sách. */
    val ControlCompact = RoundedCornerShape(12.dp)

    /** Hàng danh sách con bên trong thẻ kính (AddressCopyRow, HostAction, trường con). */
    val Row = RoundedCornerShape(14.dp)

    /** Nhãn nhỏ, huy hiệu phụ. */
    val Small = RoundedCornerShape(8.dp)

    /** Viên thuốc phân đoạn & thanh trượt tab. */
    val Chip = RoundedCornerShape(14.dp)

    /** Hộp thoại kính mờ (Dialog / Sheet). */
    val Dialog = RoundedCornerShape(24.dp)

    /** Viên thuốc tròn hoàn toàn (Status pill, nút tròn thanh đỉnh, tab pill). */
    val Pill = CircleShape

    /** Hình tròn hoàn toàn (chấm trạng thái, khung đại diện). */
    val Circle = RoundedCornerShape(percent = 50)
}

/**
 * Kích thước biểu tượng và vùng chạm chuẩn công thái học một tay trên điện thoại.
 */
object McIcon {
    /** Biểu tượng chú thích nội dòng. */
    val Inline = 16.dp

    /** Biểu tượng thao tác cạnh tiêu đề thẻ. */
    val Action = 18.dp

    /** Biểu tượng mục danh sách / thao tác chính. */
    val Item = 20.dp

    /** Biểu tượng trạng thái trống / hộp thoại. */
    val Display = 24.dp

    /** Vùng chạm tối thiểu 48dp theo chuẩn Android Accessibility. */
    val Touch = 48.dp

    /** Vùng chạm nút gọn trong hàng. */
    val TouchCompact = 48.dp
}
