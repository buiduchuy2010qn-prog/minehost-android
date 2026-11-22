package com.mineserve.mobile.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EyeCareColorPreset(val id: String, val labelVi: String) {
    SoftSlate("soft_slate", "Obsidian Slate (Tối)"),
    WarmMocha("warm_mocha", "Đêm ấm (Bảo vệ mắt)"),
    WarmPaperLight("warm_light", "Giấy sáng (Sáng)")
}

object AppUiSettings {
    private const val PREFS_NAME = "minehost_ui_appearance"
    private const val KEY_FONT_SCALE = "font_scale_multiplier_v320"
    private const val KEY_COLOR_PRESET = "eye_care_color_preset"

    // Default 1.00f pairs naturally with bundled Be Vietnam Pro + JetBrains Mono metrics.
    private val _fontScale = MutableStateFlow(1.00f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _colorPreset = MutableStateFlow(EyeCareColorPreset.SoftSlate)
    val colorPreset: StateFlow<EyeCareColorPreset> = _colorPreset.asStateFlow()

    @Volatile
    private var initialized = false

    fun ensureLoaded(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _fontScale.value = prefs.getFloat(KEY_FONT_SCALE, 1.00f).coerceIn(0.90f, 1.25f)
            val savedPresetId = prefs.getString(KEY_COLOR_PRESET, EyeCareColorPreset.SoftSlate.id)
            _colorPreset.value = EyeCareColorPreset.entries.firstOrNull { it.id == savedPresetId }
                ?: EyeCareColorPreset.SoftSlate
            initialized = true
        }
    }

    fun setFontScale(context: Context, scale: Float) {
        val clamped = scale.coerceIn(0.90f, 1.25f)
        _fontScale.value = clamped
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SCALE, clamped)
            .apply()
    }

    fun setColorPreset(context: Context, preset: EyeCareColorPreset) {
        _colorPreset.value = preset
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COLOR_PRESET, preset.id)
            .apply()
    }
}

private val McShapes = Shapes(
    extraSmall = McShape.Small,
    small = McShape.ControlCompact,
    medium = McShape.Card,
    large = McShape.Dialog,
    extraLarge = McShape.Dialog
)

private val McColorScheme = lightColorScheme(
    primary = LightIndigo,
    onPrimary = Color.White,
    primaryContainer = LightIndigoSoft,
    onPrimaryContainer = LightIndigoDark,
    secondary = LightMint,
    onSecondary = Color.White,
    secondaryContainer = LightMintSoft,
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = LightCoral,
    onTertiary = Color.White,
    tertiaryContainer = LightCoralSoft,
    onTertiaryContainer = Color(0xFF7F1D1D),
    background = LightBg,
    onBackground = LightInk,
    surface = LightCard,
    onSurface = LightInk,
    surfaceVariant = LightFieldGray,
    onSurfaceVariant = LightMuted,
    outline = LightOutline,
    outlineVariant = LightLine,
    error = LightCoral,
    onError = Color.White,
    errorContainer = LightCoralSoft,
    onErrorContainer = Color(0xFF7F1D1D),
    surfaceTint = LightIndigo,
    surfaceContainerLowest = LightContainerLowest,
    surfaceContainerLow = LightContainerLow,
    surfaceContainer = LightContainer,
    surfaceContainerHigh = LightContainerHigh,
    surfaceContainerHighest = LightContainerHighest,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    inverseSurface = LightInk,
    inverseOnSurface = LightBg,
    inversePrimary = Color(0xFF6CA0FF),
    scrim = Color(0xFF000000)
)

private val McSoftSlateDarkScheme = darkColorScheme(
    primary = Color(0xFF6CA0FF),
    onPrimary = Color(0xFF071A3A),
    primaryContainer = DarkIndigoSoft,
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = MintBright,
    onSecondary = Color(0xFF04291E),
    secondaryContainer = DarkMintSoft,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFFF87171),
    onTertiary = Color(0xFF3F0D0D),
    tertiaryContainer = DarkCoralSoft,
    onTertiaryContainer = Color(0xFFFECACA),
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkCard,
    onSurface = DarkInk,
    surfaceVariant = DarkFieldGray,
    onSurfaceVariant = DarkMuted,
    outline = DarkOutline,
    outlineVariant = DarkLine,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF45161B),
    onErrorContainer = Color(0xFFFECACA),
    surfaceTint = Color(0xFF6CA0FF),
    surfaceContainerLowest = DarkContainerLowest,
    surfaceContainerLow = DarkContainerLow,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    inverseSurface = DarkInk,
    inverseOnSurface = DarkBg,
    inversePrimary = LightIndigo,
    scrim = Color(0xFF000000)
)

private val McWarmMochaDarkScheme = darkColorScheme(
    primary = Color(0xFFE8B16E),
    onPrimary = Color(0xFF2B1803),
    primaryContainer = Color(0xFF332516),
    onPrimaryContainer = Color(0xFFFFE4C2),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF062914),
    secondaryContainer = Color(0xFF162C1E),
    onSecondaryContainer = Color(0xFFBBF7D0),
    tertiary = Color(0xFFF87171),
    onTertiary = Color(0xFF3F0D0D),
    tertiaryContainer = Color(0xFF3B1E1A),
    onTertiaryContainer = Color(0xFFFECACA),
    background = WarmDarkBg,
    onBackground = WarmDarkInk,
    surface = WarmDarkCard,
    onSurface = WarmDarkInk,
    surfaceVariant = WarmDarkFieldGray,
    onSurfaceVariant = WarmDarkMuted,
    outline = Color(0xFF8C7E72),
    outlineVariant = WarmDarkLine,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF451A18),
    onErrorContainer = Color(0xFFFECACA),
    surfaceTint = Color(0xFFE8B16E),
    surfaceContainerLowest = Color(0xFF0D0B09),
    surfaceContainerLow = Color(0xFF161311),
    surfaceContainer = Color(0xFF221E1A),
    surfaceContainerHigh = WarmDarkFieldGray,
    surfaceContainerHighest = WarmDarkLine,
    surfaceDim = WarmDarkBg,
    surfaceBright = Color(0xFF3C352F),
    inverseSurface = WarmDarkInk,
    inverseOnSurface = WarmDarkBg,
    inversePrimary = Color(0xFF9A601E),
    scrim = Color(0xFF000000)
)

internal fun mineHostColorScheme(preset: EyeCareColorPreset) = when (preset) {
    EyeCareColorPreset.SoftSlate -> McSoftSlateDarkScheme
    EyeCareColorPreset.WarmMocha -> McWarmMochaDarkScheme
    EyeCareColorPreset.WarmPaperLight -> McColorScheme
}

internal fun mineHostSemanticColors(preset: EyeCareColorPreset) = when (preset) {
    EyeCareColorPreset.SoftSlate -> ObsidianSemanticColors
    EyeCareColorPreset.WarmMocha -> WarmGraphiteSemanticColors
    EyeCareColorPreset.WarmPaperLight -> CleanPaperSemanticColors
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MineServeMobileTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        AppUiSettings.ensureLoaded(context)
    }
    val fontScaleMultiplier by AppUiSettings.fontScale.collectAsState()
    val colorPreset by AppUiSettings.colorPreset.collectAsState()
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * fontScaleMultiplier.coerceIn(0.90f, 1.25f)
    )

    val activeColorScheme = mineHostColorScheme(colorPreset)
    val semanticColors = mineHostSemanticColors(colorPreset)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !semanticColors.isDark
            controller.isAppearanceLightNavigationBars = !semanticColors.isDark
        }
    }

    MaterialTheme(
        colorScheme = activeColorScheme,
        typography = McTypography,
        shapes = McShapes
    ) {
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null,
            LocalDensity provides scaledDensity,
            LocalMineHostColors provides semanticColors
        ) {
            content()
        }
    }
}
