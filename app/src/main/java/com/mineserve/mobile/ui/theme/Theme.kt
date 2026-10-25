package com.mineserve.mobile.ui.theme

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EyeCareColorPreset(val id: String, val labelVi: String) {
    SoftSlate("soft_slate", "🌙 Xanh Đá Dịu Mắt"),
    WarmMocha("warm_mocha", "☕ Đêm Ấm Bảo Vệ Mắt"),
    WarmPaperLight("warm_light", "🌤️ Sáng Giấy Ấm")
}

object AppUiSettings {
    private const val PREFS_NAME = "minehost_ui_appearance"
    private const val KEY_FONT_SCALE = "font_scale_multiplier_v292"
    private const val KEY_COLOR_PRESET = "eye_care_color_preset"

    // Mặc định v2.9.2: cân bằng 1.04x kết hợp bảng chữ McTypography lớn rõ mà không gây tràn dòng trên màn hình hẹp
    private val _fontScale = MutableStateFlow(1.04f)
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
            _fontScale.value = prefs.getFloat(KEY_FONT_SCALE, 1.04f).coerceIn(0.90f, 1.25f)
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
    extraSmall = McShape.Row,
    small = McShape.Row,
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
    onSecondaryContainer = LightMint,
    tertiary = LightCoral,
    onTertiary = Color.White,
    tertiaryContainer = LightCoralSoft,
    onTertiaryContainer = LightCoral,
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
    onErrorContainer = Color(0xFF7A1B14),
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
    inversePrimary = Color(0xFF7AA8F8),
    scrim = Color(0xFF000000)
)

private val McSoftSlateDarkScheme = darkColorScheme(
    primary = Color(0xFF7AA8F8),
    onPrimary = Color(0xFF102347),
    primaryContainer = DarkIndigoSoft,
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = MintBright,
    onSecondary = Color(0xFF0E2F20),
    secondaryContainer = DarkMintSoft,
    onSecondaryContainer = Color(0xFFB8EBCD),
    tertiary = Color(0xFFF08C84),
    onTertiary = Color(0xFF4A1815),
    tertiaryContainer = DarkCoralSoft,
    onTertiaryContainer = Color(0xFFFFD9D6),
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkCard,
    onSurface = DarkInk,
    surfaceVariant = DarkFieldGray,
    onSurfaceVariant = DarkMuted,
    outline = DarkOutline,
    outlineVariant = DarkLine,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF6D2421),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFF7AA8F8),
    surfaceContainerLowest = DarkContainerLowest,
    surfaceContainerLow = DarkContainerLow,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    inverseSurface = DarkInk,
    inverseOnSurface = DarkBg,
    inversePrimary = Indigo,
    scrim = Color(0xFF000000)
)

private val McWarmMochaDarkScheme = darkColorScheme(
    primary = Color(0xFFE5A965),
    onPrimary = Color(0xFF382207),
    primaryContainer = Color(0xFF3D2E1E),
    onPrimaryContainer = Color(0xFFFFDFBE),
    secondary = Color(0xFF81C79B),
    onSecondary = Color(0xFF12301E),
    secondaryContainer = Color(0xFF21382A),
    onSecondaryContainer = Color(0xFFC4F0D3),
    tertiary = Color(0xFFE88D7D),
    onTertiary = Color(0xFF451912),
    tertiaryContainer = Color(0xFF3F2520),
    onTertiaryContainer = Color(0xFFFFDAD4),
    background = WarmDarkBg,
    onBackground = WarmDarkInk,
    surface = WarmDarkCard,
    onSurface = WarmDarkInk,
    surfaceVariant = WarmDarkFieldGray,
    onSurfaceVariant = WarmDarkMuted,
    outline = Color(0xFF93867B),
    outlineVariant = WarmDarkLine,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF6D2421),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFFE5A965),
    surfaceContainerLowest = Color(0xFF161311),
    surfaceContainerLow = Color(0xFF201C1A),
    surfaceContainer = WarmDarkCard,
    surfaceContainerHigh = WarmDarkFieldGray,
    surfaceContainerHighest = WarmDarkLine,
    surfaceDim = WarmDarkBg,
    surfaceBright = Color(0xFF3E3833),
    inverseSurface = WarmDarkInk,
    inverseOnSurface = WarmDarkBg,
    inversePrimary = Color(0xFFB87D3B),
    scrim = Color(0xFF000000)
)

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

    val activeColorScheme = when (colorPreset) {
        EyeCareColorPreset.SoftSlate -> McSoftSlateDarkScheme
        EyeCareColorPreset.WarmMocha -> McWarmMochaDarkScheme
        EyeCareColorPreset.WarmPaperLight -> McColorScheme
    }

    MaterialTheme(
        colorScheme = activeColorScheme,
        typography = McTypography,
        shapes = McShapes
    ) {
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null,
            LocalDensity provides scaledDensity
        ) {
            content()
        }
    }
}
