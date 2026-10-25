package com.mineserve.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Hệ thống cỡ chữ MineHost v2.9.1:
 * Nâng cấp kích thước chữ to rõ, dễ nhìn trên mọi màn hình điện thoại.
 */
private val sora = TextStyle(fontWeight = FontWeight.Bold)
private val manrope = TextStyle(fontWeight = FontWeight.Medium)

val McTypography = Typography(
    // ── Tiêu đề trang / hộp thoại ──
    headlineLarge = sora.copy(fontSize = 23.5.sp, lineHeight = 30.sp),
    headlineMedium = sora.copy(fontSize = 20.5.sp, lineHeight = 27.sp),
    headlineSmall = sora.copy(fontSize = 18.5.sp, lineHeight = 24.5.sp),

    // ── Tiêu đề thẻ / mục ──
    titleLarge = sora.copy(fontSize = 19.5.sp, lineHeight = 26.sp),
    titleMedium = sora.copy(fontSize = 16.5.sp, lineHeight = 22.5.sp),
    titleSmall = manrope.copy(fontSize = 15.0.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.5.sp),

    // ── Nội dung chính ──
    bodyLarge = manrope.copy(fontSize = 15.5.sp, lineHeight = 21.5.sp),
    bodyMedium = manrope.copy(fontSize = 14.5.sp, lineHeight = 20.sp),
    bodySmall = manrope.copy(fontSize = 13.5.sp, lineHeight = 18.5.sp),

    // ── Nhãn / nút bấm / chú thích ──
    labelLarge = manrope.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.5.sp),
    labelMedium = manrope.copy(fontSize = 13.2.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.5.sp),
    labelSmall = manrope.copy(fontSize = 12.4.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.5.sp)
)
