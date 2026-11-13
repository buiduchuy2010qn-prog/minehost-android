package com.mineserve.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Hệ thống chữ Pure Horizon Glass Mobile (v3.2.0):
 * - Tiêu đề SemiBold sắc nét lấy cảm hứng từ BlockWay PC.
 * - Chiều cao dòng rộng thoáng (1.43x–1.50x) bảo toàn trọn vẹn dấu tiếng Việt kép (ệ, ử, ẫ, ộ).
 */
private val heading = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold
)

private val body = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal
)

private val label = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium
)

val McTypography = Typography(
    // ── Tiêu đề trang / hộp thoại lớn ──
    headlineLarge = heading.copy(fontSize = 26.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    headlineMedium = heading.copy(fontSize = 23.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineSmall = heading.copy(fontSize = 20.sp, lineHeight = 30.sp, letterSpacing = (-0.15).sp),

    // ── Tiêu đề thanh đỉnh / thẻ kính / mục ──
    titleLarge = heading.copy(fontSize = 19.sp, lineHeight = 28.sp),
    titleMedium = heading.copy(fontSize = 17.sp, lineHeight = 25.sp),
    titleSmall = heading.copy(fontSize = 15.sp, lineHeight = 22.sp),

    // ── Nội dung chính (dễ đọc, thoáng dấu tiếng Việt) ──
    bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = body.copy(fontSize = 13.sp, lineHeight = 19.sp),

    // ── Nhãn nút bấm / chip trạng thái / thanh điều hướng ──
    labelLarge = label.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = label.copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = label.copy(fontSize = 11.sp, lineHeight = 16.sp)
)
