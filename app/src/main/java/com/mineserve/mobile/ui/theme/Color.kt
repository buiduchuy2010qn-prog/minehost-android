package com.mineserve.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── BlockWay Crystal Ice Light (Băng Tinh Khiết) ────────────────────────────
val LightBg = Color(0xFFF2F7FE)
val LightCard = Color(0xFFFFFFFF)
val LightInk = Color(0xFF0F1D33)
val LightMuted = Color(0xFF3E506B)
val LightLine = Color(0xFFCBD9ED)
val LightOutline = Color(0xFF6B809E)

// Màu nhấn chính: Horizon Violet-Blue & Crystal Cobalt
val Indigo = Color(0xFF7C66FF)
val IndigoDark = Color(0xFF4C35CC)
val LightIndigo = Color(0xFF1D4ED8)
val LightIndigoDark = Color(0xFF0F296B)
val LightIndigoSoft = Color(0xFFDBEAFE)

// Màu nhấn phụ: Live Emerald (Trạng thái Đang chạy / Đã cài)
val Mint = Color(0xFF34D399)
val LightMint = Color(0xFF136F4C)
val LightMintSoft = Color(0xFFD8F5E8)
val MintBright = Color(0xFF5CE698)

// Màu cảnh báo dịu mắt (Horizon Rose-Coral)
val Coral = Color(0xFFFB7185)
val LightCoral = Color(0xFFB42318)
val LightCoralSoft = Color(0xFFFCE8E8)

val LightFieldGray = Color(0xFFE6EFFB)

// ── BlockWay Horizon Dusk Dark (Tím Hoàng Hôn - Mặc định) ───────────────────
val DarkBg = Color(0xFF0E0D1E)
val DarkCard = Color(0xFF1A1730)
val DarkInk = Color(0xFFF2EFFB)
val DarkMuted = Color(0xFFC5BEE0)
val DarkLine = Color(0xFF38315C)
val DarkOutline = Color(0xFF837AA8)
val DarkIndigoSoft = Color(0xFF312463)
val DarkMintSoft = Color(0xFF163B2A)
val DarkCoralSoft = Color(0xFF471E32)
val DarkFieldGray = Color(0xFF262142)

// ── BlockWay Rust Obsidian Warm Dark (Đêm Cam Rust) ─────────────────────────
val WarmDarkBg = Color(0xFF140E0C)
val WarmDarkCard = Color(0xFF211714)
val WarmDarkInk = Color(0xFFF7ECE6)
val WarmDarkMuted = Color(0xFFD6BEB2)
val WarmDarkLine = Color(0xFF45312A)
val WarmDarkFieldGray = Color(0xFF30221D)

// ── BlockWay Mojang / Modrinth / Feather / Cherry Accent Tokens ─────────────
val MojangGreen = Color(0xFF22C55E)
val ModrinthTeal = Color(0xFF10B981)
val FeatherCyan = Color(0xFF0EA5E9)
val CherryRose = Color(0xFFEC4899)

// ── Bậc nền Surface Container ───────────────────────────────────────────────
val LightContainerLowest = Color(0xFFFFFFFF)
val LightContainerLow = Color(0xFFF7FAFF)
val LightContainer = Color(0xFFEEF5FE)
val LightContainerHigh = Color(0xFFE6EFFB)
val LightContainerHighest = Color(0xFFDCE8F8)
val LightSurfaceDim = Color(0xFFD9E6F7)
val LightSurfaceBright = Color(0xFFFFFFFF)

val DarkContainerLowest = Color(0xFF090915)
val DarkContainerLow = Color(0xFF131126)
val DarkContainer = Color(0xFF1A1730)
val DarkContainerHigh = Color(0xFF262142)
val DarkContainerHighest = Color(0xFF322C54)
val DarkSurfaceDim = Color(0xFF0E0D1E)
val DarkSurfaceBright = Color(0xFF3A3361)

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
