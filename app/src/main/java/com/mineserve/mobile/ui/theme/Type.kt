package com.mineserve.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Hệ thống cỡ chữ MineHost 2.0:
 * Tăng kích thước toàn bộ các cấp chữ (body, label, title) để đọc rõ ràng,
 * dễ nhìn trên màn hình điện thoại mà không bị mỏi mắt.
 */
private val sora = TextStyle(fontWeight = FontWeight.Bold)
private val manrope = TextStyle(fontWeight = FontWeight.Medium)

val McTypography = Typography(
    // ── Tiêu đề trang / hộp thoại (Chuẩn hóa tỷ lệ v2.2 cân đối trên mọi dòng máy) ──
    headlineLarge = sora.copy(fontSize = 22.sp, lineHeight = 29.sp),
    headlineMedium = sora.copy(fontSize = 19.sp, lineHeight = 26.sp),
    headlineSmall = sora.copy(fontSize = 17.sp, lineHeight = 23.sp),

    // ── Tiêu đề thẻ / mục ──
    titleLarge = sora.copy(fontSize = 18.5.sp, lineHeight = 25.sp),
    titleMedium = sora.copy(fontSize = 15.2.sp, lineHeight = 21.sp),
    titleSmall = manrope.copy(fontSize = 13.8.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),

    // ── Nội dung chính ──
    bodyLarge = manrope.copy(fontSize = 14.2.sp, lineHeight = 20.sp),
    bodyMedium = manrope.copy(fontSize = 13.2.sp, lineHeight = 18.5.sp),
    bodySmall = manrope.copy(fontSize = 12.2.sp, lineHeight = 17.sp),

    // ── Nhãn / nút bấm / chú thích ──
    labelLarge = manrope.copy(fontSize = 13.2.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
    labelMedium = manrope.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
    labelSmall = manrope.copy(fontSize = 11.2.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp)
)
