package com.mineserve.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── 亮色设计令牌（与参考界面完全对齐） ──────────────────────────────
val LightBg = Color(0xFFF3F4F8)
val LightCard = Color(0xFFFFFFFF)
val LightInk = Color(0xFF1F2430)
// 次要文字由 #8890A0 加深到 #6B7385：原值在纯白卡片上的对比度只有约 3.2:1，
// 11sp 的说明文字（全项目用量最大的一档）读起来发灰；新值约 4.8:1，达到 WCAG AA，
// 色相不变，因此配色观感保持一致。
val LightMuted = Color(0xFF6B7385)
// 卡片描边由 #E7E9F0 微微加深：过浅的描边在纯白卡片上几乎不可见，
// 加深后卡片边界更清晰，同时仍然足够轻，不会显得厚重。
val LightLine = Color(0xFFE4E7EE)

val Indigo = Color(0xFF14B86A)
val IndigoDark = Color(0xFF0C5934)
val LightIndigoSoft = Color(0xFFDDF7EA)

val Mint = Color(0xFF22C55E)
val LightMintSoft = Color(0xFFE3F9EC)
val MintBright = Color(0xFF86EFAC)

val Coral = Color(0xFFEF4444)
val LightCoralSoft = Color(0xFFFEE2E2)

val LightFieldGray = Color(0xFFEEF2F6)

// ── Bảng màu Minecraft Dark Charcoal & Emerald Green (Mục 4.2) ──
val DarkBg = Color(0xFF0D1318)
val DarkCard = Color(0xFF151F2A)
val DarkInk = Color(0xFFF3F6F8)
val DarkMuted = Color(0xFFA8B6C6)
val DarkLine = Color(0xFF243447)
val DarkIndigoSoft = Color(0xFF123D2B)
val DarkMintSoft = Color(0xFF143829)
val DarkCoralSoft = Color(0xFF3E1F23)
val DarkFieldGray = Color(0xFF1C2938)

// ── 容器色阶 ──────────────────────────────────────────────────────
val LightContainerLowest = Color(0xFFFFFFFF)
val LightContainerLow = Color(0xFFFAFBFC)
val LightContainer = Color(0xFFF7F8FA)
val LightContainerHigh = Color(0xFFF5F6F9)
val LightContainerHighest = Color(0xFFF0F1F5)
val LightSurfaceDim = Color(0xFFE9EBF1)
val LightSurfaceBright = Color(0xFFFFFFFF)

val DarkContainerLowest = Color(0xFF090E12)
val DarkContainerLow = Color(0xFF111922)
val DarkContainer = Color(0xFF151F2A)
val DarkContainerHigh = Color(0xFF1C2938)
val DarkContainerHighest = Color(0xFF243447)
val DarkSurfaceDim = Color(0xFF0D1318)
val DarkSurfaceBright = Color(0xFF2C3E55)

// Theme tokens come from MaterialTheme, so previews and explicit theme overrides
// remain consistent instead of silently reading the system theme again.
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
