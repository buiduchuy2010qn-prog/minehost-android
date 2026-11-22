package com.mineserve.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.R

/**
 * Bundled Google Fonts tailored for Vietnamese diacritics and fixed-width telemetry:
 * - Be Vietnam Pro: crisp, legible UI font engineered for Vietnamese accents.
 * - JetBrains Mono (tnum): aligned tabular numerals for TPS, RAM, IP:Port, and console logs.
 */
val BeVietnamProFamily = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold)
)

val McMetricStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 23.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum"
)

val McCodeStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 19.sp,
    fontFeatureSettings = "tnum"
)

private val heading = TextStyle(fontFamily = BeVietnamProFamily, fontWeight = FontWeight.SemiBold)
private val body = TextStyle(fontFamily = BeVietnamProFamily, fontWeight = FontWeight.Normal)

val McTypography = Typography(
    headlineLarge = heading.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineMedium = heading.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    headlineSmall = heading.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),

    titleLarge = heading.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleMedium = heading.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp),
    titleSmall = heading.copy(fontSize = 14.sp, lineHeight = 20.sp),

    bodyLarge = body.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = body.copy(fontSize = 12.5.sp, lineHeight = 18.sp),

    labelLarge = heading.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = heading.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = heading.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.2.sp)
)
