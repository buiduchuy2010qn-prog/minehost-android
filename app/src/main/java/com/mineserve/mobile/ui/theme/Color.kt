package com.mineserve.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Bảng màu Sáng Giấy Ấm Dịu Mắt (Warm Paper Light) ──────────────────────
val LightBg = Color(0xFFF5F7FA)
val LightCard = Color(0xFFFFFFFF)
val LightInk = Color(0xFF172033)
val LightMuted = Color(0xFF596579)
val LightLine = Color(0xFFDCE2EC)
val LightOutline = Color(0xFF7D899C)

// Màu nhấn chính: Xanh Lam Đá Dịu Mắt (Nord Calm Sky-Blue) thay cho xanh lục neon chói mắt
val Indigo = Color(0xFF4F86E8)
val IndigoDark = Color(0xFF2D56A6)
val LightIndigo = Color(0xFF3559D9)
val LightIndigoDark = Color(0xFF2544AA)
val LightIndigoSoft = Color(0xFFEBF0FF)

// Màu nhấn phụ: Xanh Ngọc Bích Trầm (Muted Sage) cho trạng thái Đang chạy / Đã cài
val Mint = Color(0xFF3E9B74)
val LightMint = Color(0xFF247A54)
val LightMintSoft = Color(0xFFE2F5EC)
val MintBright = Color(0xFF6BC497)

// Màu cảnh báo dịu mắt (Soft Rose-Coral)
val Coral = Color(0xFFD95C5C)
val LightCoral = Color(0xFFB42318)
val LightCoralSoft = Color(0xFFFCE8E8)

val LightFieldGray = Color(0xFFF0F3F8)

// ── Bảng màu Tối Xanh Đá Nord Dịu Mắt (Soft Slate Dark - Mặc định) ────────
val DarkBg = Color(0xFF181C24)
val DarkCard = Color(0xFF212733)
val DarkInk = Color(0xFFE6ECF5)
val DarkMuted = Color(0xFFA7B4C8)
val DarkLine = Color(0xFF313A4B)
val DarkOutline = Color(0xFF718099)
val DarkIndigoSoft = Color(0xFF233454)
val DarkMintSoft = Color(0xFF1E3A31)
val DarkCoralSoft = Color(0xFF3F262A)
val DarkFieldGray = Color(0xFF283040)

// ── Bảng màu Đêm Ấm Giảm Ánh Sáng Xanh (Warm Mocha Eye-Care) ──────────────
val WarmDarkBg = Color(0xFF1C1917)
val WarmDarkCard = Color(0xFF26221F)
val WarmDarkInk = Color(0xFFEDE6DF)
val WarmDarkMuted = Color(0xFFB8ACA0)
val WarmDarkLine = Color(0xFF3B3530)
val WarmDarkFieldGray = Color(0xFF302B27)

// ── Container色阶 ──────────────────────────────────────────────────────
val LightContainerLowest = Color(0xFFFFFFFF)
val LightContainerLow = Color(0xFFF7F9FC)
val LightContainer = Color(0xFFF2F5FA)
val LightContainerHigh = Color(0xFFECF0F7)
val LightContainerHighest = Color(0xFFE5EAF2)
val LightSurfaceDim = Color(0xFFE2E7F0)
val LightSurfaceBright = Color(0xFFFFFFFF)

val DarkContainerLowest = Color(0xFF141820)
val DarkContainerLow = Color(0xFF1C212B)
val DarkContainer = Color(0xFF212733)
val DarkContainerHigh = Color(0xFF283040)
val DarkContainerHighest = Color(0xFF313A4B)
val DarkSurfaceDim = Color(0xFF181C24)
val DarkSurfaceBright = Color(0xFF364052)

val Bg: Color @Composable get() = MaterialTheme.colorScheme.background
val Card: Color @Composable get() = MaterialTheme.colorScheme.surface
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val IndigoSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val MintSoft: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val CoralSoft: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
val FieldBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val TrackBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val FieldGray: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
