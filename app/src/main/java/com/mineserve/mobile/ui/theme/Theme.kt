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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EyeCareColorPreset(val id: String, val labelVi: String) {
    SoftSlate("soft_slate", "🌌 Tím Hoàng Hôn"),
    BlockWayDark("blockway_dark", "💎 Xanh Đêm Glass"),
    WarmMocha("warm_mocha", "🦀 Đêm Cam Rust"),
    SakuraGlass("sakura_glass", "🌸 Hồng Sakura"),
    WarmPaperLight("warm_light", "❄️ Băng Tinh Khiết"),
    MojangEmerald("mojang_emerald", "🌿 Xanh Lục Mojang"),
    ModrinthMint("modrinth_mint", "🧪 Xanh Mint Modrinth"),
    FeatherSky("feather_sky", "🪶 Xanh Băng Feather"),
    CherryDark("cherry_dark", "🍒 Tối Hồng Cherry")
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
    val dangerCoralSoft: Color,
    val skyStops: List<Color> = emptyList(),
    val bloomColor: Color = Color.Transparent,
    val ridgeFar: Color = Color.Transparent,
    val ridgeNear: Color = Color.Transparent,
    val showStars: Boolean = !isLight
)

private val SkyHorizonDusk = listOf(
    Color(0xFF090914), Color(0xFF0B0A18), Color(0xFF0E0D1E), Color(0xFF121026), Color(0xFF16132E),
    Color(0xFF1B1636), Color(0xFF22193F), Color(0xFF2C1D49), Color(0xFF3B2254), Color(0xFF52295F),
    Color(0xFF6E3266), Color(0xFF8E3E68), Color(0xFFB05166), Color(0xFFCE6A62), Color(0xFFE48663)
)

private val SkyLunarGlass = listOf(
    Color(0xFF060A16), Color(0xFF091022), Color(0xFF0D1730), Color(0xFF122042), Color(0xFF182B56),
    Color(0xFF1F386D), Color(0xFF284786), Color(0xFF3359A0), Color(0xFF406CB8), Color(0xFF4F80CE),
    Color(0xFF6195DF), Color(0xFF76AAEC), Color(0xFF8EBFF5), Color(0xFFA7D2FB), Color(0xFFC2E3FF)
)

private val SkyRustNight = listOf(
    Color(0xFF0D0809), Color(0xFF130B0C), Color(0xFF1B0F10), Color(0xFF261413), Color(0xFF331B17),
    Color(0xFF43231B), Color(0xFF572D20), Color(0xFF6F3926), Color(0xFF8A482D), Color(0xFFA75A35),
    Color(0xFFC36E3F), Color(0xFFDC834C), Color(0xFFEF995D), Color(0xFFF8AE72), Color(0xFFFFC48C)
)

private val SkySakuraDawn = listOf(
    Color(0xFFD6E2FC), Color(0xFFDCE0FA), Color(0xFFE4DFF9), Color(0xFFEEDCF8), Color(0xFFF4DBF4),
    Color(0xFFF9DAEF), Color(0xFFFFD9EA), Color(0xFFFFDBE6), Color(0xFFFFDDE2), Color(0xFFFFDFDE),
    Color(0xFFFFE1DB), Color(0xFFFFE2D9), Color(0xFFFFE4D6), Color(0xFFFFE8DA), Color(0xFFFFEDE0)
)

private val SkyPrismIce = listOf(
    Color(0xFFC5DCFB), Color(0xFFCDE1FC), Color(0xFFD5E6FD), Color(0xFFDCEBFE), Color(0xFFE3F0FF),
    Color(0xFFE9F3FF), Color(0xFFEFF6FF), Color(0xFFF4F6FD), Color(0xFFF8F4F8), Color(0xFFFBF2F0),
    Color(0xFFFFEFE6), Color(0xFFFFEDDF), Color(0xFFFFEAD9), Color(0xFFFFECDC), Color(0xFFFFF0E2)
)

private val SkyMojangEmerald = listOf(
    Color(0xFF060F0B), Color(0xFF091610), Color(0xFF0D1F17), Color(0xFF122A1F), Color(0xFF183728),
    Color(0xFF204533), Color(0xFF29563F), Color(0xFF34684D), Color(0xFF417C5C), Color(0xFF50916E),
    Color(0xFF62A681), Color(0xFF76BB95), Color(0xFF8DD0AA), Color(0xFFA6E2BF), Color(0xFFC2F2D6)
)

private val SkyModrinthMint = listOf(
    Color(0xFF061010), Color(0xFF081717), Color(0xFF0C2120), Color(0xFF112C2B), Color(0xFF163937),
    Color(0xFF1D4845), Color(0xFF265955), Color(0xFF306B66), Color(0xFF3C7F79), Color(0xFF4A948D),
    Color(0xFF5BAAA2), Color(0xFF6FC0B7), Color(0xFF86D5CC), Color(0xFFA0E8DF), Color(0xFFBCF6EE)
)

private val SkyFeatherIce = listOf(
    Color(0xFF050C15), Color(0xFF081320), Color(0xFF0C1B2D), Color(0xFF11253D), Color(0xFF17314F),
    Color(0xFF1E3F63), Color(0xFF274F79), Color(0xFF316090), Color(0xFF3D73A8), Color(0xFF4B87BF),
    Color(0xFF5C9CD4), Color(0xFF70B2E6), Color(0xFF87C7F3), Color(0xFFA1DBFB), Color(0xFFBCEBFF)
)

private val SkyCherryDark = listOf(
    Color(0xFF0D0711), Color(0xFF140B1A), Color(0xFF1C1025), Color(0xFF271633), Color(0xFF341D43),
    Color(0xFF432656), Color(0xFF56316B), Color(0xFF6B3D81), Color(0xFF834C98), Color(0xFF9C5DAE),
    Color(0xFFB670C3), Color(0xFFCF86D7), Color(0xFFE49EE8), Color(0xFFF3B7F4), Color(0xFFFDD2FD)
)

fun horizonGlassTokensFor(
    preset: EyeCareColorPreset,
    glassOpacity: Float = 0.26f
): HorizonGlassTokens {
    val op = glassOpacity.coerceIn(0.14f, 0.52f)
    val darkCardStrongAlpha = (op + 0.06f).coerceAtMost(0.56f)
    val darkRowAlpha = (op * 0.27f).coerceIn(0.055f, 0.13f)
    val darkChromeAlpha = (op + 0.08f).coerceIn(0.20f, 0.54f)

    val lightCardAlpha = (op + 0.18f).coerceIn(0.28f, 0.66f)
    val lightCardStrongAlpha = (op + 0.24f).coerceIn(0.34f, 0.72f)
    val lightChromeAlpha = (op + 0.22f).coerceIn(0.32f, 0.70f)

    return when (preset) {
        EyeCareColorPreset.SoftSlate -> HorizonGlassTokens(
            isLight = false,
            skyTop = Color(0xFF090914),
            skyMid = Color(0xFF2C1D49),
            horizonGlow = Color(0xFFCE6A62),
            auroraPrimary = Color(0xFF7C5CFF).copy(alpha = 0.34f),
            auroraSecondary = Color(0xFFE056A0).copy(alpha = 0.30f),
            glassCard = Color(0xFF3A3163).copy(alpha = op),
            glassCardStrong = Color(0xFF423870).copy(alpha = darkCardStrongAlpha),
            glassRow = Color.White.copy(alpha = darkRowAlpha),
            glassChrome = Color(0xFF28214A).copy(alpha = darkChromeAlpha),
            glassInput = Color.White.copy(alpha = 0.08f),
            hairline = Color.White.copy(alpha = 0.20f),
            specularTop = Color.White.copy(alpha = 0.46f),
            accent = Color(0xFF9B82FF),
            accentSoft = Color(0x388B5CF6),
            liveGreen = Color(0xFF4ADE80),
            liveGreenSoft = Color(0x294ADE80),
            skyCyan = Color(0xFF38BDF8),
            warnAmber = Color(0xFFFBBF24),
            warnAmberSoft = Color(0x29FBBF24),
            dangerCoral = Color(0xFFFB7185),
            dangerCoralSoft = Color(0x29FB7185),
            skyStops = SkyHorizonDusk,
            bloomColor = Color(0xFFF2946C).copy(alpha = 0.34f),
            ridgeFar = Color(0xFF231236),
            ridgeNear = Color(0xFF140A22),
            showStars = true
        )

        EyeCareColorPreset.BlockWayDark -> HorizonGlassTokens(
            isLight = false,
            skyTop = Color(0xFF060A16),
            skyMid = Color(0xFF284786),
            horizonGlow = Color(0xFF8EBFF5),
            auroraPrimary = Color(0xFF3B82F6).copy(alpha = 0.28f),
            auroraSecondary = Color(0xFF38BDF8).copy(alpha = 0.26f),
            glassCard = Color(0xFF22365C).copy(alpha = op),
            glassCardStrong = Color(0xFF2E4876).copy(alpha = darkCardStrongAlpha),
            glassRow = Color.White.copy(alpha = darkRowAlpha),
            glassChrome = Color(0xFF162542).copy(alpha = darkChromeAlpha),
            glassInput = Color.White.copy(alpha = 0.08f),
            hairline = Color.White.copy(alpha = 0.20f),
            specularTop = Color.White.copy(alpha = 0.46f),
            accent = Color(0xFF60A5FA),
            accentSoft = Color(0x363B82F6),
            liveGreen = Color(0xFF34D399),
            liveGreenSoft = Color(0x2934D399),
            skyCyan = Color(0xFF67E8F9),
            warnAmber = Color(0xFFFBBF24),
            warnAmberSoft = Color(0x29FBBF24),
            dangerCoral = Color(0xFFFB7185),
            dangerCoralSoft = Color(0x29FB7185),
            skyStops = SkyLunarGlass,
            bloomColor = Color(0xFF60A5FA).copy(alpha = 0.34f),
            ridgeFar = Color(0xFF10203D),
            ridgeNear = Color(0xFF081124),
            showStars = true
        )

        EyeCareColorPreset.WarmMocha -> HorizonGlassTokens(
            isLight = false,
            skyTop = Color(0xFF0D0809),
            skyMid = Color(0xFF572D20),
            horizonGlow = Color(0xFFEF995D),
            auroraPrimary = Color(0xFFF97316).copy(alpha = 0.28f),
            auroraSecondary = Color(0xFFF43F5E).copy(alpha = 0.26f),
            glassCard = Color(0xFF482A24).copy(alpha = op),
            glassCardStrong = Color(0xFF583630).copy(alpha = darkCardStrongAlpha),
            glassRow = Color.White.copy(alpha = darkRowAlpha),
            glassChrome = Color(0xFF2E1A17).copy(alpha = darkChromeAlpha),
            glassInput = Color.White.copy(alpha = 0.08f),
            hairline = Color.White.copy(alpha = 0.20f),
            specularTop = Color.White.copy(alpha = 0.46f),
            accent = Color(0xFFFB923C),
            accentSoft = Color(0x36FB923C),
            liveGreen = Color(0xFF4ADE80),
            liveGreenSoft = Color(0x294ADE80),
            skyCyan = Color(0xFF38BDF8),
            warnAmber = Color(0xFFFBBF24),
            warnAmberSoft = Color(0x29FBBF24),
            dangerCoral = Color(0xFFFB7185),
            dangerCoralSoft = Color(0x29FB7185),
            skyStops = SkyRustNight,
            bloomColor = Color(0xFFFB923C).copy(alpha = 0.34f),
            ridgeFar = Color(0xFF28120E),
            ridgeNear = Color(0xFF150807),
            showStars = true
        )

        EyeCareColorPreset.SakuraGlass -> HorizonGlassTokens(
            isLight = true,
            skyTop = Color(0xFFD6E2FC),
            skyMid = Color(0xFFEEDCF8),
            horizonGlow = Color(0xFFFFD9EA),
            auroraPrimary = Color(0xFFF472B6).copy(alpha = 0.28f),
            auroraSecondary = Color(0xFFFB923C).copy(alpha = 0.24f),
            glassCard = Color.White.copy(alpha = lightCardAlpha),
            glassCardStrong = Color.White.copy(alpha = lightCardStrongAlpha),
            glassRow = Color.White.copy(alpha = 0.34f),
            glassChrome = Color.White.copy(alpha = lightChromeAlpha),
            glassInput = Color.White.copy(alpha = 0.44f),
            hairline = Color(0xFFDB2777).copy(alpha = 0.22f),
            specularTop = Color.White.copy(alpha = 0.85f),
            accent = Color(0xFFDB2777),
            accentSoft = Color(0x26EC4899),
            liveGreen = Color(0xFF15803D),
            liveGreenSoft = Color(0x2422C55E),
            skyCyan = Color(0xFF0284C7),
            warnAmber = Color(0xFFB45309),
            warnAmberSoft = Color(0x24F59E0B),
            dangerCoral = Color(0xFFBE123C),
            dangerCoralSoft = Color(0x24F43F5E),
            skyStops = SkySakuraDawn,
            bloomColor = Color(0xFFF472B6).copy(alpha = 0.32f),
            ridgeFar = Color(0xFFD9ABC7),
            ridgeNear = Color(0xFFC28AAD),
            showStars = false
        )

        EyeCareColorPreset.WarmPaperLight -> HorizonGlassTokens(
            isLight = true,
            skyTop = Color(0xFFC5DCFB),
            skyMid = Color(0xFFDCEBFE),
            horizonGlow = Color(0xFFFFEAD9),
            auroraPrimary = Color(0xFF3B82F6).copy(alpha = 0.26f),
            auroraSecondary = Color(0xFF0EA5E9).copy(alpha = 0.24f),
            glassCard = Color.White.copy(alpha = lightCardAlpha),
            glassCardStrong = Color.White.copy(alpha = lightCardStrongAlpha),
            glassRow = Color.White.copy(alpha = 0.34f),
            glassChrome = Color.White.copy(alpha = lightChromeAlpha),
            glassInput = Color.White.copy(alpha = 0.44f),
            hairline = Color(0xFF2563EB).copy(alpha = 0.20f),
            specularTop = Color.White.copy(alpha = 0.85f),
            accent = Color(0xFF2563EB),
            accentSoft = Color(0x242563EB),
            liveGreen = Color(0xFF15803D),
            liveGreenSoft = Color(0x2422C55E),
            skyCyan = Color(0xFF0284C7),
            warnAmber = Color(0xFFB45309),
            warnAmberSoft = Color(0x24F59E0B),
            dangerCoral = Color(0xFFBE123C),
            dangerCoralSoft = Color(0x24F43F5E),
            skyStops = SkyPrismIce,
            bloomColor = Color(0xFF60A5FA).copy(alpha = 0.30f),
            ridgeFar = Color(0xFFADC7E6),
            ridgeNear = Color(0xFF8BAED6),
            showStars = false
        )

        EyeCareColorPreset.MojangEmerald -> HorizonGlassTokens(
            isLight = false,
            skyTop = Color(0xFF060F0B),
            skyMid = Color(0xFF29563F),
            horizonGlow = Color(0xFF8DD0AA),
            auroraPrimary = Color(0xFF22C55E).copy(alpha = 0.28f),
            auroraSecondary = Color(0xFF10B981).copy(alpha = 0.26f),
            glassCard = Color(0xFF223E30).copy(alpha = op),
            glassCardStrong = Color(0xFF2E503E).copy(alpha = darkCardStrongAlpha),
            glassRow = Color.White.copy(alpha = darkRowAlpha),
            glassChrome = Color(0xFF162A20).copy(alpha = darkChromeAlpha),
            glassInput = Color.White.copy(alpha = 0.08f),
            hairline = Color.White.copy(alpha = 0.20f),
            specularTop = Color.White.copy(alpha = 0.46f),
            accent = Color(0xFF4ADE80),
            accentSoft = Color(0x3622C55E),
            liveGreen = Color(0xFF4ADE80),
            liveGreenSoft = Color(0x294ADE80),
            skyCyan = Color(0xFF38BDF8),
            warnAmber = Color(0xFFFBBF24),
            warnAmberSoft = Color(0x29FBBF24),
            dangerCoral = Color(0xFFFB7185),
            dangerCoralSoft = Color(0x29FB7185),
            skyStops = SkyMojangEmerald,
            bloomColor = Color(0xFF4ADE80).copy(alpha = 0.34f),
            ridgeFar = Color(0xFF102A1F),
            ridgeNear = Color(0xFF081610),
            showStars = true
        )

        EyeCareColorPreset.ModrinthMint -> HorizonGlassTokens(
            isLight = false,
            skyTop = Color(0xFF061010),
            skyMid = Color(0xFF265955),
            horizonGlow = Color(0xFF86D5CC),
            auroraPrimary = Color(0xFF10B981).copy(alpha = 0.28f),
            auroraSecondary = Color(0xFF2DD4BF).copy(alpha = 0.26f),
            glassCard = Color(0xFF20403C).copy(alpha = op),
            glassCardStrong = Color(0xFF2C524E).copy(alpha = darkCardStrongAlpha),
            glassRow = Color.White.copy(alpha = darkRowAlpha),
            glassChrome = Color(0xFF142A28).copy(alpha = darkChromeAlpha),
            glassInput = Color.White.copy(alpha = 0.08f),
            hairline = Color.White.copy(alpha = 0.20f),
            specularTop = Color.White.copy(alpha = 0.46f),
            accent = Color(0xFF34D399),
            accentSoft = Color(0x3610B981),
            liveGreen = Color(0xFF34D399),
            liveGreenSoft = Color(0x2934D399),
            skyCyan = Color(0xFF2DD4BF),
            warnAmber = Color(0xFFFBBF24),
            warnAmberSoft = Color(0x29FBBF24),
            dangerCoral = Color(0xFFFB7185),
            dangerCoralSoft = Color(0x29FB7185),
            skyStops = SkyModrinthMint,
            bloomColor = Color(0xFF34D399).copy(alpha = 0.34f),
            ridgeFar = Color(0xFF0F2927),
            ridgeNear = Color(0xFF071514),
            showStars = true
        )

        EyeCareColorPreset.FeatherSky -> HorizonGlassTokens(
            isLight = false,
            skyTop = Color(0xFF050C15),
            skyMid = Color(0xFF274F79),
            horizonGlow = Color(0xFF87C7F3),
            auroraPrimary = Color(0xFF0EA5E9).copy(alpha = 0.28f),
            auroraSecondary = Color(0xFF38BDF8).copy(alpha = 0.26f),
            glassCard = Color(0xFF203A56).copy(alpha = op),
            glassCardStrong = Color(0xFF2C4C6E).copy(alpha = darkCardStrongAlpha),
            glassRow = Color.White.copy(alpha = darkRowAlpha),
            glassChrome = Color(0xFF14263B).copy(alpha = darkChromeAlpha),
            glassInput = Color.White.copy(alpha = 0.08f),
            hairline = Color.White.copy(alpha = 0.20f),
            specularTop = Color.White.copy(alpha = 0.46f),
            accent = Color(0xFF38BDF8),
            accentSoft = Color(0x360EA5E9),
            liveGreen = Color(0xFF4ADE80),
            liveGreenSoft = Color(0x294ADE80),
            skyCyan = Color(0xFF67E8F9),
            warnAmber = Color(0xFFFBBF24),
            warnAmberSoft = Color(0x29FBBF24),
            dangerCoral = Color(0xFFFB7185),
            dangerCoralSoft = Color(0x29FB7185),
            skyStops = SkyFeatherIce,
            bloomColor = Color(0xFF38BDF8).copy(alpha = 0.34f),
            ridgeFar = Color(0xFF0F2840),
            ridgeNear = Color(0xFF071523),
            showStars = true
        )

        EyeCareColorPreset.CherryDark -> HorizonGlassTokens(
            isLight = false,
            skyTop = Color(0xFF0D0711),
            skyMid = Color(0xFF56316B),
            horizonGlow = Color(0xFFE49EE8),
            auroraPrimary = Color(0xFFEC4899).copy(alpha = 0.28f),
            auroraSecondary = Color(0xFFA855F7).copy(alpha = 0.26f),
            glassCard = Color(0xFF462642).copy(alpha = op),
            glassCardStrong = Color(0xFF5A3254).copy(alpha = darkCardStrongAlpha),
            glassRow = Color.White.copy(alpha = darkRowAlpha),
            glassChrome = Color(0xFF2C162B).copy(alpha = darkChromeAlpha),
            glassInput = Color.White.copy(alpha = 0.08f),
            hairline = Color.White.copy(alpha = 0.20f),
            specularTop = Color.White.copy(alpha = 0.46f),
            accent = Color(0xFFF472B6),
            accentSoft = Color(0x36EC4899),
            liveGreen = Color(0xFF4ADE80),
            liveGreenSoft = Color(0x294ADE80),
            skyCyan = Color(0xFF38BDF8),
            warnAmber = Color(0xFFFBBF24),
            warnAmberSoft = Color(0x29FBBF24),
            dangerCoral = Color(0xFFFB7185),
            dangerCoralSoft = Color(0x29FB7185),
            skyStops = SkyCherryDark,
            bloomColor = Color(0xFFF472B6).copy(alpha = 0.34f),
            ridgeFar = Color(0xFF281028),
            ridgeNear = Color(0xFF150815),
            showStars = true
        )
    }
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
        glass.accent.copy(alpha = if (glass.isLight) 0.20f else 0.24f)
    } else {
        Color.White.copy(alpha = if (glass.isLight) 0.36f else 0.13f)
    }
    val middleFrost = if (highlighted) {
        glass.accent.copy(alpha = if (glass.isLight) 0.06f else 0.07f)
    } else {
        Color.White.copy(alpha = if (glass.isLight) 0.10f else 0.025f)
    }
    val bottomSheen = if (highlighted) {
        glass.specularTop.copy(alpha = if (glass.isLight) 0.18f else 0.06f)
    } else {
        Color.White.copy(alpha = if (glass.isLight) 0.16f else 0.05f)
    }
    val topStroke = if (highlighted) {
        glass.accent.copy(alpha = if (glass.isLight) 0.84f else 0.92f)
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
            brush = Brush.linearGradient(colors = listOf(topSheen, middleFrost, bottomSheen)),
            shape = shape
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(colors = listOf(topStroke, bottomStroke)),
            shape = shape
        )
}

private class Mulberry32(seed: Int) {
    private var a: Int = seed
    fun nextFloat(): Float {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        val unsigned = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
        return (unsigned.toDouble() / 4294967296.0).toFloat()
    }
}

private fun hash1D(x: Int, seed: Int): Float {
    var h = (x xor seed) * 0x27D4EB2D
    h = h xor (h ushr 15)
    return ((h and 0xFFFF).toFloat()) / 65535f
}

private fun valueNoise1D(x: Float, seed: Int): Float {
    val xi = floor(x).toInt()
    val xf = x - xi.toFloat()
    val u = xf * xf * (3f - 2f * xf)
    return hash1D(xi, seed) * (1f - u) + hash1D(xi + 1, seed) * u
}

private fun fbm1D(x: Float, seed: Int): Float {
    var v = 0f
    var amp = 0.5f
    var freq = 1f
    for (i in 0 until 4) {
        v += amp * valueNoise1D(x * freq, seed + i * 97)
        freq *= 2.03f
        amp *= 0.5f
    }
    return v
}

private data class HorizonStar(
    val xRatio: Float,
    val yRatio: Float,
    val alpha: Float,
    val sizeFactor: Float,
    val hasHalo: Boolean
)

private val HorizonStarField: List<HorizonStar> = buildList(90) {
    val rng = Mulberry32(19)
    repeat(90) { index ->
        val sx = rng.nextFloat()
        val sy = rng.nextFloat() * 0.72f
        val alpha = (0.22f + rng.nextFloat() * 0.64f).coerceAtMost(0.90f)
        val sizeFactor = when {
            index % 8 == 0 -> 1.65f
            index % 3 == 0 -> 1.25f
            else -> 0.92f
        }
        add(HorizonStar(sx, sy, alpha, sizeFactor, index % 8 == 0))
    }
}

private fun buildHorizonRidgePath(
    width: Float,
    height: Float,
    baseRatio: Float,
    ampRatio: Float,
    freq: Float,
    seed: Int
): Path {
    val path = Path()
    val steps = 88
    for (i in 0..steps) {
        val ratioX = i.toFloat() / steps.toFloat()
        val x = ratioX * width
        val h = fbm1D(ratioX * freq, seed)
        val y = height * (baseRatio - (h - 0.35f) * ampRatio)
        if (i == 0) {
            path.moveTo(0f, height)
            path.lineTo(0f, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.lineTo(width, height)
    path.close()
    return path
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
                val stopsSource = if (glass.skyStops.size >= 2) {
                    glass.skyStops
                } else {
                    listOf(glass.skyTop, glass.skyMid, glass.horizonGlow)
                }
                val lastIdx = (stopsSource.size - 1).coerceAtLeast(1)
                val skyColorStops = Array(stopsSource.size) { idx ->
                    val linearT = idx.toFloat() / lastIdx.toFloat()
                    linearT.pow(1.55f) to stopsSource[idx]
                }
                val skyBrush = Brush.verticalGradient(
                    colorStops = skyColorStops,
                    startY = 0f,
                    endY = safeH
                )
                val auroraPrimaryBrush = Brush.radialGradient(
                    colors = listOf(glass.auroraPrimary, Color.Transparent),
                    center = Offset(safeW * 0.22f, safeH * 0.28f),
                    radius = (maxDim * 0.48f).coerceAtLeast(1f)
                )
                val auroraSecondaryBrush = Brush.radialGradient(
                    colors = listOf(glass.auroraSecondary, Color.Transparent),
                    center = Offset(safeW * 0.80f, safeH * 0.44f),
                    radius = (maxDim * 0.44f).coerceAtLeast(1f)
                )
                val bloomColor = if (glass.bloomColor != Color.Transparent) {
                    glass.bloomColor
                } else {
                    glass.horizonGlow.copy(alpha = 0.34f)
                }
                val horizonBloomBrush = Brush.radialGradient(
                    colors = listOf(bloomColor, Color.Transparent),
                    center = Offset(safeW * 0.68f, safeH * 0.88f),
                    radius = (maxDim * 0.44f).coerceAtLeast(1f)
                )
                val farRidgePath = buildHorizonRidgePath(
                    width = safeW,
                    height = safeH,
                    baseRatio = 0.865f,
                    ampRatio = 0.072f,
                    freq = 3.4f,
                    seed = 17
                )
                val nearRidgePath = buildHorizonRidgePath(
                    width = safeW,
                    height = safeH,
                    baseRatio = 0.918f,
                    ampRatio = 0.052f,
                    freq = 5.1f,
                    seed = 41
                )
                val baseStarPx = 1.15.dp.toPx().coerceAtLeast(1f)

                onDrawBehind {
                    drawRect(skyBrush)
                    drawRect(auroraPrimaryBrush)
                    drawRect(auroraSecondaryBrush)
                    if (glass.showStars) {
                        HorizonStarField.forEach { star ->
                            val center = Offset(star.xRatio * safeW, star.yRatio * safeH)
                            val radius = baseStarPx * star.sizeFactor
                            if (star.hasHalo) {
                                drawCircle(
                                    color = Color.White.copy(alpha = star.alpha * 0.22f),
                                    radius = radius * 2.4f,
                                    center = center
                                )
                            }
                            drawCircle(
                                color = Color.White.copy(alpha = star.alpha),
                                radius = radius,
                                center = center
                            )
                        }
                    }
                    drawRect(horizonBloomBrush)
                    if (glass.ridgeFar != Color.Transparent) {
                        drawPath(path = farRidgePath, color = glass.ridgeFar)
                    }
                    if (glass.ridgeNear != Color.Transparent) {
                        drawPath(path = nearRidgePath, color = glass.ridgeNear)
                    }
                }
            }
    )
}

object AppUiSettings {
    private const val PREFS_NAME = "minehost_ui_appearance"
    private const val KEY_FONT_SCALE = "font_scale_multiplier_v292"
    private const val KEY_COLOR_PRESET = "eye_care_color_preset"
    private const val KEY_GLASS_OPACITY = "horizon_glass_opacity_v321"

    private val _fontScale = MutableStateFlow(1.04f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _colorPreset = MutableStateFlow(EyeCareColorPreset.SoftSlate)
    val colorPreset: StateFlow<EyeCareColorPreset> = _colorPreset.asStateFlow()

    private val _glassOpacity = MutableStateFlow(0.26f)
    val glassOpacity: StateFlow<Float> = _glassOpacity.asStateFlow()

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
            _glassOpacity.value = prefs.getFloat(KEY_GLASS_OPACITY, 0.26f).coerceIn(0.14f, 0.52f)
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

    fun setGlassOpacity(context: Context, opacity: Float) {
        val clamped = opacity.coerceIn(0.14f, 0.52f)
        _glassOpacity.value = clamped
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_GLASS_OPACITY, clamped)
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

// 6. 🌿 Xanh Lục Mojang (MojangEmerald — Emerald Night Dark Glass, WCAG >= 9.3:1)
private val McMojangEmeraldDarkScheme = darkColorScheme(
    primary = Color(0xFF6EE798),
    onPrimary = Color(0xFF052E14),
    primaryContainer = Color(0xFF144227),
    onPrimaryContainer = Color(0xFFD6FCE3),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F28),
    secondaryContainer = Color(0xFF113D36),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFF93C5FD),
    onTertiary = Color(0xFF0A2547),
    tertiaryContainer = Color(0xFF16355E),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF08130F),
    onBackground = Color(0xFFECFDF3),
    surface = Color(0xFF0F1F18),
    onSurface = Color(0xFFECFDF3),
    surfaceVariant = Color(0xFF182E24),
    onSurfaceVariant = Color(0xFFB5D8C5),
    outline = Color(0xFF6E9983),
    outlineVariant = Color(0xFF254537),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1D24),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFF6EE798),
    surfaceContainerLowest = Color(0xFF060F0B),
    surfaceContainerLow = Color(0xFF0B1812),
    surfaceContainer = Color(0xFF0F1F18),
    surfaceContainerHigh = Color(0xFF182E24),
    surfaceContainerHighest = Color(0xFF223D31),
    surfaceDim = Color(0xFF08130F),
    surfaceBright = Color(0xFF2B4C3D),
    inverseSurface = Color(0xFFECFDF3),
    inverseOnSurface = Color(0xFF08130F),
    inversePrimary = Color(0xFF15803D),
    scrim = Color(0xFF000000)
)

// 7. 🧪 Xanh Mint Modrinth (ModrinthMint — Mint Teal Dark Glass, WCAG >= 9.3:1)
private val McModrinthMintDarkScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F28),
    primaryContainer = Color(0xFF11423B),
    onPrimaryContainer = Color(0xFFD2FBF3),
    secondary = Color(0xFF6EE798),
    onSecondary = Color(0xFF052E14),
    secondaryContainer = Color(0xFF143D25),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFF7DD3FC),
    onTertiary = Color(0xFF06283B),
    tertiaryContainer = Color(0xFF123A52),
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF071313),
    onBackground = Color(0xFFECFEFC),
    surface = Color(0xFF0E1F1E),
    onSurface = Color(0xFFECFEFC),
    surfaceVariant = Color(0xFF172E2C),
    onSurfaceVariant = Color(0xFFB2D8D4),
    outline = Color(0xFF6A9994),
    outlineVariant = Color(0xFF244542),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1D24),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFF5EEAD4),
    surfaceContainerLowest = Color(0xFF061010),
    surfaceContainerLow = Color(0xFF0A1817),
    surfaceContainer = Color(0xFF0E1F1E),
    surfaceContainerHigh = Color(0xFF172E2C),
    surfaceContainerHighest = Color(0xFF213E3B),
    surfaceDim = Color(0xFF071313),
    surfaceBright = Color(0xFF294D49),
    inverseSurface = Color(0xFFECFEFC),
    inverseOnSurface = Color(0xFF071313),
    inversePrimary = Color(0xFF0D9488),
    scrim = Color(0xFF000000)
)

// 8. 🪶 Xanh Băng Feather (FeatherSky — Ice Sky Dark Glass, WCAG >= 8.7:1)
private val McFeatherSkyDarkScheme = darkColorScheme(
    primary = Color(0xFF67D4FF),
    onPrimary = Color(0xFF04283B),
    primaryContainer = Color(0xFF113A54),
    onPrimaryContainer = Color(0xFFD6F3FF),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F28),
    secondaryContainer = Color(0xFF113D36),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFA5B4FC),
    onTertiary = Color(0xFF121C4F),
    tertiaryContainer = Color(0xFF222F66),
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF07111D),
    onBackground = Color(0xFFEDF6FF),
    surface = Color(0xFF0E1B2B),
    onSurface = Color(0xFFEDF6FF),
    surfaceVariant = Color(0xFF172A40),
    onSurfaceVariant = Color(0xFFB4CBE3),
    outline = Color(0xFF6D8AA8),
    outlineVariant = Color(0xFF243D59),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1D24),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFF67D4FF),
    surfaceContainerLowest = Color(0xFF050C15),
    surfaceContainerLow = Color(0xFF0A1522),
    surfaceContainer = Color(0xFF0E1B2B),
    surfaceContainerHigh = Color(0xFF172A40),
    surfaceContainerHighest = Color(0xFF213954),
    surfaceDim = Color(0xFF07111D),
    surfaceBright = Color(0xFF2A4768),
    inverseSurface = Color(0xFFEDF6FF),
    inverseOnSurface = Color(0xFF07111D),
    inversePrimary = Color(0xFF0284C7),
    scrim = Color(0xFF000000)
)

// 9. 🍒 Tối Hồng Cherry (CherryDark — Cherry Blossom Night Glass, WCAG >= 7.99:1)
private val McCherryDarkScheme = darkColorScheme(
    primary = Color(0xFFFF94CC),
    onPrimary = Color(0xFF420828),
    primaryContainer = Color(0xFF521B3B),
    onPrimaryContainer = Color(0xFFFFDCEB),
    secondary = Color(0xFF6EE798),
    onSecondary = Color(0xFF052E14),
    secondaryContainer = Color(0xFF153B27),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFFD8B4FE),
    onTertiary = Color(0xFF331054),
    tertiaryContainer = Color(0xFF48206E),
    onTertiaryContainer = Color(0xFFF3E8FF),
    background = Color(0xFF120917),
    onBackground = Color(0xFFFCF0F8),
    surface = Color(0xFF211322),
    onSurface = Color(0xFFFCF0F8),
    surfaceVariant = Color(0xFF301D33),
    onSurfaceVariant = Color(0xFFDDBBD9),
    outline = Color(0xFF9E789A),
    outlineVariant = Color(0xFF472B4B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1D24),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFFFF94CC),
    surfaceContainerLowest = Color(0xFF0D0711),
    surfaceContainerLow = Color(0xFF180E1B),
    surfaceContainer = Color(0xFF211322),
    surfaceContainerHigh = Color(0xFF301D33),
    surfaceContainerHighest = Color(0xFF3E2642),
    surfaceDim = Color(0xFF120917),
    surfaceBright = Color(0xFF4D3052),
    inverseSurface = Color(0xFFFCF0F8),
    inverseOnSurface = Color(0xFF120917),
    inversePrimary = Color(0xFFBE185D),
    scrim = Color(0xFF000000)
)

fun mineHostColorScheme(preset: EyeCareColorPreset): ColorScheme = when (preset) {
    EyeCareColorPreset.SoftSlate -> McSoftSlateDarkScheme
    EyeCareColorPreset.BlockWayDark -> McBlockWayDarkScheme
    EyeCareColorPreset.WarmMocha -> McWarmMochaDarkScheme
    EyeCareColorPreset.SakuraGlass -> McSakuraLightScheme
    EyeCareColorPreset.WarmPaperLight -> McCrystalIceLightScheme
    EyeCareColorPreset.MojangEmerald -> McMojangEmeraldDarkScheme
    EyeCareColorPreset.ModrinthMint -> McModrinthMintDarkScheme
    EyeCareColorPreset.FeatherSky -> McFeatherSkyDarkScheme
    EyeCareColorPreset.CherryDark -> McCherryDarkScheme
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
    val glassOpacity by AppUiSettings.glassOpacity.collectAsState()
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity.density, baseDensity.fontScale, fontScaleMultiplier) {
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * fontScaleMultiplier.coerceIn(0.90f, 1.25f)
        )
    }

    val activeColorScheme = mineHostColorScheme(colorPreset)
    val activeGlass = remember(colorPreset, glassOpacity) {
        horizonGlassTokensFor(colorPreset, glassOpacity)
    }

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
