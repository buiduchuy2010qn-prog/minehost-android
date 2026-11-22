package com.mineserve.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Clean Paper (Light) ─────────────────────────────────────────────────────
val LightBg = Color(0xFFF6F8FB)
val LightCard = Color(0xFFFFFFFF)
val LightInk = Color(0xFF0F172A)
val LightMuted = Color(0xFF475569)
val LightLine = Color(0xFFDCE3EC)
val LightOutline = Color(0xFF64748B)

val Indigo = Color(0xFF4F8BFF)
val IndigoDark = Color(0xFF1E40AF)
val LightIndigo = Color(0xFF2563EB)
val LightIndigoDark = Color(0xFF102D6B)
val LightIndigoSoft = Color(0xFFE0ECFF)

val Mint = Color(0xFF10B981)
val LightMint = Color(0xFF047857)
val LightMintSoft = Color(0xFFD1FAE5)
val MintBright = Color(0xFF34D399)

val Coral = Color(0xFFEF4444)
val LightCoral = Color(0xFFDC2626)
val LightCoralSoft = Color(0xFFFEE2E2)

val LightFieldGray = Color(0xFFEDF1F7)

// ── Obsidian Slate (Dark Default) ───────────────────────────────────────────
val DarkBg = Color(0xFF0B0E14)
val DarkCard = Color(0xFF121721)
val DarkInk = Color(0xFFF1F5F9)
val DarkMuted = Color(0xFFA3B1C6)
val DarkLine = Color(0xFF263042)
val DarkOutline = Color(0xFF64748B)
val DarkIndigoSoft = Color(0xFF172540)
val DarkMintSoft = Color(0xFF102B22)
val DarkCoralSoft = Color(0xFF3A1B20)
val DarkFieldGray = Color(0xFF1B2332)

// ── Warm Graphite (Warm Eye-Care Dark) ──────────────────────────────────────
val WarmDarkBg = Color(0xFF12100E)
val WarmDarkCard = Color(0xFF1B1815)
val WarmDarkInk = Color(0xFFF4EDE6)
val WarmDarkMuted = Color(0xFFB8ACA0)
val WarmDarkLine = Color(0xFF352F29)
val WarmDarkFieldGray = Color(0xFF26211D)

// ── Surface Hierarchy Containers ────────────────────────────────────────────
val LightContainerLowest = Color(0xFFFFFFFF)
val LightContainerLow = Color(0xFFF8FAFC)
val LightContainer = Color(0xFFF1F5F9)
val LightContainerHigh = Color(0xFFE2E8F0)
val LightContainerHighest = Color(0xFFD5DEE9)
val LightSurfaceDim = Color(0xFFE6ECF3)
val LightSurfaceBright = Color(0xFFFFFFFF)

val DarkContainerLowest = Color(0xFF080A0F)
val DarkContainerLow = Color(0xFF0F141D)
val DarkContainer = Color(0xFF181F2C)
val DarkContainerHigh = Color(0xFF20293A)
val DarkContainerHighest = Color(0xFF283347)
val DarkSurfaceDim = Color(0xFF0B0E14)
val DarkSurfaceBright = Color(0xFF2F3B52)

// ── Semantic Extended Tokens (WCAG AA Checked for Light & Dark) ─────────────
@Immutable
data class MineHostSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val rust: Color,
    val onRust: Color,
    val rustContainer: Color,
    val onRustContainer: Color,
    val info: Color,
    val terminalBg: Color,
    val terminalSurface: Color,
    val terminalBorder: Color,
    val isDark: Boolean
)

val ObsidianSemanticColors = MineHostSemanticColors(
    success = Color(0xFF34D399),
    onSuccess = Color(0xFF04291E),
    successContainer = Color(0xFF0F2D23),
    onSuccessContainer = Color(0xFFA7F3D0),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF2E1E02),
    warningContainer = Color(0xFF2E2209),
    onWarningContainer = Color(0xFFFDE68A),
    rust = Color(0xFFFB923C),
    onRust = Color(0xFF2B1202),
    rustContainer = Color(0xFF2B180E),
    onRustContainer = Color(0xFFFED7AA),
    info = Color(0xFF38BDF8),
    terminalBg = Color(0xFF080B10),
    terminalSurface = Color(0xFF111621),
    terminalBorder = Color(0xFF222B3B),
    isDark = true
)

val WarmGraphiteSemanticColors = MineHostSemanticColors(
    success = Color(0xFF4ADE80),
    onSuccess = Color(0xFF062914),
    successContainer = Color(0xFF162C1E),
    onSuccessContainer = Color(0xFFBBF7D0),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF2E1E02),
    warningContainer = Color(0xFF2F2310),
    onWarningContainer = Color(0xFFFDE68A),
    rust = Color(0xFFFB923C),
    onRust = Color(0xFF2B1202),
    rustContainer = Color(0xFF301B10),
    onRustContainer = Color(0xFFFED7AA),
    info = Color(0xFFE5A965),
    terminalBg = Color(0xFF0D0B09),
    terminalSurface = Color(0xFF191512),
    terminalBorder = Color(0xFF312A24),
    isDark = true
)

val CleanPaperSemanticColors = MineHostSemanticColors(
    success = Color(0xFF047857),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFD1FAE5),
    onSuccessContainer = Color(0xFF064E3B),
    warning = Color(0xFFB45309),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF78350F),
    rust = Color(0xFFC2410C),
    onRust = Color(0xFFFFFFFF),
    rustContainer = Color(0xFFFFEDD5),
    onRustContainer = Color(0xFF7C2D12),
    info = Color(0xFF0284C7),
    terminalBg = Color(0xFF0B0E14),
    terminalSurface = Color(0xFF131924),
    terminalBorder = Color(0xFF253042),
    isDark = false
)

val LocalMineHostColors = staticCompositionLocalOf { ObsidianSemanticColors }

val MaterialTheme.mineHostColors: MineHostSemanticColors
    @Composable get() = LocalMineHostColors.current

// ── Theme-Reactive Legacy Token Aliases (Preserved for Screen Compatibility) ─
val Bg: Color @Composable get() = MaterialTheme.colorScheme.background
val Card: Color @Composable get() = MaterialTheme.colorScheme.surface
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val IndigoSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val MintSoft: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val CoralSoft: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
val FieldBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
val TrackBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val FieldGray: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
