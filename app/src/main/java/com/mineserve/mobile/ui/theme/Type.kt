package com.mineserve.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Hệ thống cỡ chữ MineHost v2.9.1:
 * Nâng cấp kích thước chữ to rõ, dễ nhìn trên mọi màn hình điện thoại.
 */
private val heading = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium)
private val body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal)

val McTypography = Typography(
    // ── Tiêu đề trang / hộp thoại ──
    headlineLarge = heading.copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = heading.copy(fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = heading.copy(fontSize = 22.sp, lineHeight = 30.sp),

    // ── Tiêu đề thẻ / mục ──
    titleLarge = heading.copy(fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = heading.copy(fontSize = 18.sp, lineHeight = 26.sp),
    titleSmall = heading.copy(fontSize = 16.sp, lineHeight = 24.sp),

    // ── Nội dung chính ──
    bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = body.copy(fontSize = 14.sp, lineHeight = 20.sp),

    // ── Nhãn / nút bấm / chú thích ──
    labelLarge = heading.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = heading.copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = heading.copy(fontSize = 11.sp, lineHeight = 16.sp)
)
