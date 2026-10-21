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
    // ── Tiêu đề trang / hộp thoại ──
    headlineLarge = sora.copy(fontSize = 26.sp, lineHeight = 34.sp),
    headlineMedium = sora.copy(fontSize = 22.sp, lineHeight = 30.sp),
    headlineSmall = sora.copy(fontSize = 19.sp, lineHeight = 26.sp),

    // ── Tiêu đề thẻ / mục ──
    titleLarge = sora.copy(fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = sora.copy(fontSize = 16.5.sp, lineHeight = 23.sp),
    titleSmall = manrope.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),

    // ── Nội dung chính (Tăng từ 11-13sp lên 13-16sp) ──
    bodyLarge = manrope.copy(fontSize = 15.5.sp, lineHeight = 22.sp),
    bodyMedium = manrope.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = manrope.copy(fontSize = 13.sp, lineHeight = 18.sp),

    // ── Nhãn / nút bấm / chú thích (Tăng từ 10-12sp lên 12-14sp) ──
    labelLarge = manrope.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),
    labelMedium = manrope.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp),
    labelSmall = manrope.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
)
