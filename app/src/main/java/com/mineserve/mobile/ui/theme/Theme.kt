package com.mineserve.mobile.ui.theme

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EyeCareColorPreset(val id: String, val labelVi: String) {
    SoftSlate("soft_slate", "🌌 Tím Hoàng Hôn"),
    BlockWayDark("blockway_dark", "💎 Xanh Đêm Glass"),
    WarmMocha("warm_mocha", "🦀 Đêm Cam Rust"),
    SakuraGlass("sakura_glass", "🌸 Hồng Sakura"),
    WarmPaperLight("warm_light", "❄️ Băng Tinh Khiết")
}

data class HorizonGlassTokens(
    val isLight: Boolean,
    val skyTop: Color,
    val skyMid: Color,
    val horizonGlow: Color,
    val auroraPrimary: Color,
    val auroraSecondary: Color,
    val glassCard: Color,
    val glassCardStrong: Color,
    val glassRow: Color,
    val glassChrome: Color,
    val glassInput: Color,
    val hairline: Color,
    val specularTop: Color,
    val accent: Color,
    val accentSoft: Color,
    val liveGreen: Color,
    val liveGreenSoft: Color,
    val skyCyan: Color,
    val warnAmber: Color,
    val warnAmberSoft: Color,
    val dangerCoral: Color,
    val dangerCoralSoft: Color
)

fun horizonGlassTokensFor(preset: EyeCareColorPreset): HorizonGlassTokens = when (preset) {
    EyeCareColorPreset.SoftSlate -> HorizonGlassTokens(
        isLight = false,
        skyTop = Color(0xFF090915),
        skyMid = Color(0xFF16132E),
        horizonGlow = Color(0xFF4A2559),
        auroraPrimary = Color(0x2E7C5CFF),
        auroraSecondary = Color(0x26EC4899),
        glassCard = Color(0x782D274B),
        glassCardStrong = Color(0xAD221D3D),
        glassRow = Color(0x8C252040),
        glassChrome = Color(0xE0141126),
        glassInput = Color(0x9E1C1833),
        hairline = Color(0x24FFFFFF),
        specularTop = Color(0x42FFFFFF),
        accent = Color(0xFF9B82FF),
        accentSoft = Color(0x338B5CF6),
        liveGreen = Color(0xFF4ADE80),
        liveGreenSoft = Color(0x294ADE80),
        skyCyan = Color(0xFF38BDF8),
        warnAmber = Color(0xFFFBBF24),
        warnAmberSoft = Color(0x29FBBF24),
        dangerCoral = Color(0xFFFB7185),
        dangerCoralSoft = Color(0x29FB7185)
    )

    EyeCareColorPreset.BlockWayDark -> HorizonGlassTokens(
        isLight = false,
        skyTop = Color(0xFF060D18),
        skyMid = Color(0xFF0D1D33),
        horizonGlow = Color(0xFF143C5E),
        auroraPrimary = Color(0x2E38BDF8),
        auroraSecondary = Color(0x246366F1),
        glassCard = Color(0x78172942),
        glassCardStrong = Color(0xAD112036),
        glassRow = Color(0x8C13233B),
        glassChrome = Color(0xE00A1526),
        glassInput = Color(0x9E101E33),
        hairline = Color(0x24BAE6FD),
        specularTop = Color(0x42E0F2FE),
        accent = Color(0xFF38BDF8),
        accentSoft = Color(0x3038BDF8),
        liveGreen = Color(0xFF34D399),
        liveGreenSoft = Color(0x2934D399),
        skyCyan = Color(0xFF67E8F9),
        warnAmber = Color(0xFFFBBF24),
        warnAmberSoft = Color(0x29FBBF24),
        dangerCoral = Color(0xFFFB7185),
        dangerCoralSoft = Color(0x29FB7185)
    )

    EyeCareColorPreset.WarmMocha -> HorizonGlassTokens(
        isLight = false,
        skyTop = Color(0xFF110B0A),
        skyMid = Color(0xFF211412),
        horizonGlow = Color(0xFF54251B),
        auroraPrimary = Color(0x2EF97316),
        auroraSecondary = Color(0x24F43F5E),
        glassCard = Color(0x7836231E),
        glassCardStrong = Color(0xAD291B17),
        glassRow = Color(0x8C2E1E1A),
        glassChrome = Color(0xE018100E),
        glassInput = Color(0x9E241714),
        hairline = Color(0x24FED7AA),
        specularTop = Color(0x40FFEDD5),
        accent = Color(0xFFFB923C),
        accentSoft = Color(0x30FB923C),
        liveGreen = Color(0xFF4ADE80),
        liveGreenSoft = Color(0x294ADE80),
        skyCyan = Color(0xFF38BDF8),
        warnAmber = Color(0xFFFBBF24),
        warnAmberSoft = Color(0x29FBBF24),
        dangerCoral = Color(0xFFFB7185),
        dangerCoralSoft = Color(0x29FB7185)
    )

    EyeCareColorPreset.SakuraGlass -> HorizonGlassTokens(
        isLight = true,
        skyTop = Color(0xFFFFEDF4),
        skyMid = Color(0xFFFFF7FA),
        horizonGlow = Color(0xFFFBCFE8),
        auroraPrimary = Color(0x29EC4899),
        auroraSecondary = Color(0x21A855F7),
        glassCard = Color(0xCCFFFFFF),
        glassCardStrong = Color(0xEBFFFFFF),
        glassRow = Color(0xBDFFF4F8),
        glassChrome = Color(0xEBFFF8FB),
        glassInput = Color(0xD9FFF0F6),
        hairline = Color(0x29DB2777),
        specularTop = Color(0xCCFFFFFF),
        accent = Color(0xFFDB2777),
        accentSoft = Color(0x24EC4899),
        liveGreen = Color(0xFF15803D),
        liveGreenSoft = Color(0x2422C55E),
        skyCyan = Color(0xFF0284C7),
        warnAmber = Color(0xFFB45309),
        warnAmberSoft = Color(0x24F59E0B),
        dangerCoral = Color(0xFFBE123C),
        dangerCoralSoft = Color(0x24F43F5E)
    )

    EyeCareColorPreset.WarmPaperLight -> HorizonGlassTokens(
        isLight = true,
        skyTop = Color(0xFFDCEBFC),
        skyMid = Color(0xFFF4F9FF),
        horizonGlow = Color(0xFFC7E0FE),
        auroraPrimary = Color(0x263B82F6),
        auroraSecondary = Color(0x2106B6D4),
        glassCard = Color(0xCCFFFFFF),
        glassCardStrong = Color(0xEBFFFFFF),
        glassRow = Color(0xBDF0F7FF),
        glassChrome = Color(0xEBF7FBFF),
        glassInput = Color(0xD9EDF5FF),
        hairline = Color(0x262563EB),
        specularTop = Color(0xCCFFFFFF),
        accent = Color(0xFF2563EB),
        accentSoft = Color(0x222563EB),
        liveGreen = Color(0xFF15803D),
        liveGreenSoft = Color(0x2422C55E),
        skyCyan = Color(0xFF0284C7),
        warnAmber = Color(0xFFB45309),
        warnAmberSoft = Color(0x24F59E0B),
        dangerCoral = Color(0xFFBE123C),
        dangerCoralSoft = Color(0x24F43F5E)
    )
}

val LocalHorizonGlass = staticCompositionLocalOf { horizonGlassTokensFor(EyeCareColorPreset.SoftSlate) }

val HorizonGlass: HorizonGlassTokens
    @Composable get() = LocalHorizonGlass.current

val HorizonTabularStyle: TextStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun Modifier.horizonGlassPlate(
    shape: Shape = McShape.Card,
    containerColor: Color = HorizonGlass.glassCard,
    borderColor: Color = HorizonGlass.hairline,
    highlighted: Boolean = false
): Modifier {
    val glass = HorizonGlass
    val topSheen = if (highlighted) {
        glass.accent.copy(alpha = if (glass.isLight) 0.14f else 0.18f)
    } else {
        glass.specularTop.copy(alpha = if (glass.isLight) 0.36f else 0.10f)
    }
    val bottomSheen = if (highlighted) {
        glass.accent.copy(alpha = 0.03f)
    } else {
        Color.Transparent
    }
    val topStroke = if (highlighted) {
        glass.accent.copy(alpha = if (glass.isLight) 0.80f else 0.90f)
    } else {
        glass.specularTop
    }
    val bottomStroke = if (highlighted) {
        glass.accent.copy(alpha = if (glass.isLight) 0.38f else 0.46f)
    } else {
        borderColor
    }
    return this
        .clip(shape)
        .background(containerColor, shape)
        .background(
            brush = Brush.verticalGradient(colors = listOf(topSheen, bottomSheen)),
            shape = shape
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(colors = listOf(topStroke, bottomStroke)),
            shape = shape
        )
}

@Composable
fun HorizonGlassBackdrop(modifier: Modifier = Modifier) {
    val glass = HorizonGlass
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val safeW = size.width.coerceAtLeast(1f)
                val safeH = size.height.coerceAtLeast(1f)
                val maxDim = maxOf(safeW, safeH)
                val skyBrush = Brush.verticalGradient(
                    colors = listOf(glass.skyTop, glass.skyMid),
                    startY = 0f,
                    endY = safeH
                )
                val horizonStart = safeH * 0.74f
                val horizonBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        glass.horizonGlow.copy(alpha = if (glass.isLight) 0.45f else 0.55f),
                        glass.horizonGlow.copy(alpha = if (glass.isLight) 0.82f else 0.90f)
                    ),
                    startY = horizonStart,
                    endY = safeH
                )
                val auroraTopBrush = Brush.radialGradient(
                    colors = listOf(glass.auroraPrimary, Color.Transparent),
                    center = Offset(safeW * 0.22f, safeH * 0.15f),
                    radius = (maxDim * 0.62f).coerceAtLeast(1f)
                )
                val auroraBottomBrush = Brush.radialGradient(
                    colors = listOf(glass.auroraSecondary, Color.Transparent),
                    center = Offset(safeW * 0.84f, safeH * 0.86f),
                    radius = (maxDim * 0.56f).coerceAtLeast(1f)
                )
                onDrawBehind {
                    drawRect(skyBrush)
                    drawRect(horizonBrush)
                    drawRect(auroraTopBrush)
                    drawRect(auroraBottomBrush)
                }
            }
    )
}

object AppUiSettings {
    private const val PREFS_NAME = "minehost_ui_appearance"
    private const val KEY_FONT_SCALE = "font_scale_multiplier_v292"
    private const val KEY_COLOR_PRESET = "eye_care_color_preset"

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

// 1. 🌌 Tím Hoàng Hôn (SoftSlate — Default BlockWay Horizon Dusk, WCAG >= 7.8:1)
private val McSoftSlateDarkScheme = darkColorScheme(
    primary = Color(0xFFB39DFF),
    onPrimary = Color(0xFF180C45),
    primaryContainer = Color(0xFF312463),
    onPrimaryContainer = Color(0xFFE8E0FF),
    secondary = Color(0xFF5CE698),
    onSecondary = Color(0xFF062E19),
    secondaryContainer = Color(0xFF163B2A),
    onSecondaryContainer = Color(0xFFC7F9DC),
    tertiary = Color(0xFFFF96B8),
    onTertiary = Color(0xFF420C22),
    tertiaryContainer = Color(0xFF471E32),
    onTertiaryContainer = Color(0xFFFFD9E5),
    background = Color(0xFF0E0D1E),
    onBackground = Color(0xFFF2EFFB),
    surface = Color(0xFF1A1730),
    onSurface = Color(0xFFF2EFFB),
    surfaceVariant = Color(0xFF262142),
    onSurfaceVariant = Color(0xFFC5BEE0),
    outline = Color(0xFF837AA8),
    outlineVariant = Color(0xFF38315C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1D24),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFFB39DFF),
    surfaceContainerLowest = Color(0xFF090915),
    surfaceContainerLow = Color(0xFF131126),
    surfaceContainer = Color(0xFF1A1730),
    surfaceContainerHigh = Color(0xFF262142),
    surfaceContainerHighest = Color(0xFF322C54),
    surfaceDim = Color(0xFF0E0D1E),
    surfaceBright = Color(0xFF3A3361),
    inverseSurface = Color(0xFFF2EFFB),
    inverseOnSurface = Color(0xFF0E0D1E),
    inversePrimary = Color(0xFF5B3FE0),
    scrim = Color(0xFF000000)
)

// 2. 💎 Xanh Đêm Glass (BlockWayDark — Lunar Cyan-Blue Dark, WCAG >= 8.4:1)
private val McBlockWayDarkScheme = darkColorScheme(
    primary = Color(0xFF6CD3FF),
    onPrimary = Color(0xFF04273B),
    primaryContainer = Color(0xFF123854),
    onPrimaryContainer = Color(0xFFD6F2FF),
    secondary = Color(0xFF5CE6A0),
    onSecondary = Color(0xFF052E1B),
    secondaryContainer = Color(0xFF123B2B),
    onSecondaryContainer = Color(0xFFC4F9DF),
    tertiary = Color(0xFFA6B4FF),
    onTertiary = Color(0xFF101D52),
    tertiaryContainer = Color(0xFF213066),
    onTertiaryContainer = Color(0xFFE0E5FF),
    background = Color(0xFF08111E),
    onBackground = Color(0xFFECF4FF),
    surface = Color(0xFF101C2E),
    onSurface = Color(0xFFECF4FF),
    surfaceVariant = Color(0xFF192A42),
    onSurfaceVariant = Color(0xFFB3C7E3),
    outline = Color(0xFF6E87A8),
    outlineVariant = Color(0xFF263B59),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1D24),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFF6CD3FF),
    surfaceContainerLowest = Color(0xFF060D18),
    surfaceContainerLow = Color(0xFF0C1626),
    surfaceContainer = Color(0xFF101C2E),
    surfaceContainerHigh = Color(0xFF192A42),
    surfaceContainerHighest = Color(0xFF233856),
    surfaceDim = Color(0xFF08111E),
    surfaceBright = Color(0xFF2B4466),
    inverseSurface = Color(0xFFECF4FF),
    inverseOnSurface = Color(0xFF08111E),
    inversePrimary = Color(0xFF0284C7),
    scrim = Color(0xFF000000)
)

// 3. 🦀 Đêm Cam Rust (WarmMocha — Rust Obsidian Warm Dark, WCAG >= 8.6:1)
private val McWarmMochaDarkScheme = darkColorScheme(
    primary = Color(0xFFFFAB70),
    onPrimary = Color(0xFF3B1702),
    primaryContainer = Color(0xFF52260D),
    onPrimaryContainer = Color(0xFFFFE2CF),
    secondary = Color(0xFF70E09C),
    onSecondary = Color(0xFF082E18),
    secondaryContainer = Color(0xFF193B26),
    onSecondaryContainer = Color(0xFFC9F7DA),
    tertiary = Color(0xFFFF9EAA),
    onTertiary = Color(0xFF45111B),
    tertiaryContainer = Color(0xFF542029),
    onTertiaryContainer = Color(0xFFFFDADF),
    background = Color(0xFF140E0C),
    onBackground = Color(0xFFF7ECE6),
    surface = Color(0xFF211714),
    onSurface = Color(0xFFF7ECE6),
    surfaceVariant = Color(0xFF30221D),
    onSurfaceVariant = Color(0xFFD6BEB2),
    outline = Color(0xFF9E8275),
    outlineVariant = Color(0xFF45312A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1D24),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFFFFAB70),
    surfaceContainerLowest = Color(0xFF100B09),
    surfaceContainerLow = Color(0xFF1A1210),
    surfaceContainer = Color(0xFF211714),
    surfaceContainerHigh = Color(0xFF30221D),
    surfaceContainerHighest = Color(0xFF3D2B25),
    surfaceDim = Color(0xFF140E0C),
    surfaceBright = Color(0xFF47332C),
    inverseSurface = Color(0xFFF7ECE6),
    inverseOnSurface = Color(0xFF140E0C),
    inversePrimary = Color(0xFFC2410C),
    scrim = Color(0xFF000000)
)

// 4. 🌸 Hồng Sakura (SakuraGlass — Sakura Dawn Light Glass, WCAG >= 6.17:1)
private val McSakuraLightScheme = lightColorScheme(
    primary = Color(0xFFB81D65),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E8),
    onPrimaryContainer = Color(0xFF54082C),
    secondary = Color(0xFF166E4A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9F7E8),
    onSecondaryContainer = Color(0xFF0A3D27),
    tertiary = Color(0xFF7A2E99),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E1FF),
    onTertiaryContainer = Color(0xFF3B0952),
    background = Color(0xFFFFF6F9),
    onBackground = Color(0xFF2B1321),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B1321),
    surfaceVariant = Color(0xFFFCE8F0),
    onSurfaceVariant = Color(0xFF6B4258),
    outline = Color(0xFF9C6E85),
    outlineVariant = Color(0xFFF0C9DA),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8E8),
    onErrorContainer = Color(0xFF69110B),
    surfaceTint = Color(0xFFB81D65),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF8FB),
    surfaceContainer = Color(0xFFFFEFF5),
    surfaceContainerHigh = Color(0xFFFCE8F0),
    surfaceContainerHighest = Color(0xFFF7DCE8),
    surfaceDim = Color(0xFFF5D8E5),
    surfaceBright = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF2B1321),
    inverseOnSurface = Color(0xFFFFF6F9),
    inversePrimary = Color(0xFFFF9EC7),
    scrim = Color(0xFF000000)
)

// 5. ❄️ Băng Tinh Khiết (WarmPaperLight — Crystal Ice Light Glass, WCAG >= 6.70:1)
private val McCrystalIceLightScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0F296B),
    secondary = Color(0xFF136F4C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8F5E8),
    onSecondaryContainer = Color(0xFF083B27),
    tertiary = Color(0xFF0369A1),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF082F49),
    background = Color(0xFFF2F7FE),
    onBackground = Color(0xFF0F1D33),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1D33),
    surfaceVariant = Color(0xFFE6EFFB),
    onSurfaceVariant = Color(0xFF3E506B),
    outline = Color(0xFF6B809E),
    outlineVariant = Color(0xFFCBD9ED),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8E8),
    onErrorContainer = Color(0xFF69110B),
    surfaceTint = Color(0xFF1D4ED8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAFF),
    surfaceContainer = Color(0xFFEEF5FE),
    surfaceContainerHigh = Color(0xFFE6EFFB),
    surfaceContainerHighest = Color(0xFFDCE8F8),
    surfaceDim = Color(0xFFD9E6F7),
    surfaceBright = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF0F1D33),
    inverseOnSurface = Color(0xFFF2F7FE),
    inversePrimary = Color(0xFF93C5FD),
    scrim = Color(0xFF000000)
)

fun mineHostColorScheme(preset: EyeCareColorPreset): ColorScheme = when (preset) {
    EyeCareColorPreset.SoftSlate -> McSoftSlateDarkScheme
    EyeCareColorPreset.BlockWayDark -> McBlockWayDarkScheme
    EyeCareColorPreset.WarmMocha -> McWarmMochaDarkScheme
    EyeCareColorPreset.SakuraGlass -> McSakuraLightScheme
    EyeCareColorPreset.WarmPaperLight -> McCrystalIceLightScheme
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
    val activeGlass = remember(colorPreset) { horizonGlassTokensFor(colorPreset) }

    MaterialTheme(
        colorScheme = activeColorScheme,
        typography = McTypography,
        shapes = McShapes
    ) {
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null,
            LocalDensity provides scaledDensity,
            LocalHorizonGlass provides activeGlass,
            LocalContentColor provides activeColorScheme.onSurface
        ) {
            content()
        }
    }
}
