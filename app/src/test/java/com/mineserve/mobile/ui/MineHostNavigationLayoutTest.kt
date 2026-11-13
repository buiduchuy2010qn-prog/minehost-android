package com.mineserve.mobile.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mineserve.mobile.ui.screens.AdaptiveActionRow
import com.mineserve.mobile.ui.screens.HostAction
import com.mineserve.mobile.ui.screens.HostDisclosure
import com.mineserve.mobile.ui.theme.EyeCareColorPreset
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonGlassBackdrop
import com.mineserve.mobile.ui.theme.LocalHorizonGlass
import com.mineserve.mobile.ui.theme.McTypography
import com.mineserve.mobile.ui.theme.horizonGlassTokensFor
import com.mineserve.mobile.ui.theme.mineHostColorScheme
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Runs on the GitHub runner, with no server/VM/network side effects. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w320dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MineHostNavigationLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null

    private fun show(
        fontScale: Float = 1f,
        dark: Boolean = false,
        preset: EyeCareColorPreset = if (dark) EyeCareColorPreset.SoftSlate else EyeCareColorPreset.WarmPaperLight,
        content: @Composable () -> Unit
    ) {
        controller?.let {
            it.pause().stop().destroy()
            controller = null
        }
        val scheme = mineHostColorScheme(preset)
        val glass = horizonGlassTokensFor(preset)
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller!!.get().setContent {
            MaterialTheme(colorScheme = scheme, typography = McTypography) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                    LocalHorizonGlass provides glass
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        contentColor = scheme.onSurface
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            HorizonGlassBackdrop()
                            content()
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun closeActivity() {
        controller?.pause()?.stop()?.destroy()
        controller = null
    }

    private fun assertTextFits() {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                layouts.forEach { result ->
                    // Cached paragraph width can exceed the final intrinsic text width.
                    // Check the actual glyph lines, ellipsis and height, not that cache width.
                    repeat(result.lineCount) { line ->
                        val clipped = result.isLineEllipsized(line) ||
                            result.getLineLeft(line) < -1f ||
                            result.getLineRight(line) > result.size.width + 1f ||
                            result.getLineBottom(line) > result.size.height + 1f
                        assertFalse(
                            "Text clipped: ${result.layoutInput.text}; line=$line; bounds=${result.getLineLeft(line)}..${result.getLineRight(line)}; size=${result.size}",
                            clipped
                        )
                    }
                }
            }
    }

    private fun screenshot(name: String) {
        val file = File("build/reports/ui-layout/$name.png")
        file.parentFile?.mkdirs()
        // Draw the actual view on the runner; PixelCopy requires a real display.
        compose.runOnUiThread {
            val view = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
                ?: controller!!.get().window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Composable
    private fun HorizonMobileScrollablePreview(scrollState: ScrollState) {
        val glass = HorizonGlass
        MineHostNavigation(
            title = "Máy chủ",
            serverName = "Paper 1.21.4",
            tab = McTab.Dashboard,
            page = null,
            onTab = {},
            onNavigate = {},
            liveStatusPillText = "🟢 20.0 TPS • 36°C",
            liveStatusState = 2
        ) {
            val navChrome = LocalHorizonChromePadding.current
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0.dp),
                bottomBar = { MineHostBottomBar(selected = McTab.Dashboard, onSelect = {}) }
            ) { scaffoldPadding ->
                val fullChrome = PaddingValues(
                    top = navChrome.calculateTopPadding(),
                    bottom = maxOf(navChrome.calculateBottomPadding(), scaffoldPadding.calculateBottomPadding())
                )
                CompositionLocalProvider(LocalHorizonChromePadding provides fullChrome) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("horizon-scroll-viewport")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(
                                    top = fullChrome.calculateTopPadding() + 6.dp,
                                    bottom = fullChrome.calculateBottomPadding() + 20.dp
                                ),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            McCard(
                                title = "Trạng thái máy chủ",
                                trailing = { StatusPill("Online", glass.liveGreen) }
                            ) {
                                Text(
                                    "Không gian kính pha lê Pure Horizon Glass xuyên thấu bầu trời sao và hoàng hôn.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SegPill("Java Mod", selected = true, modifier = Modifier.weight(1f)) {}
                                    SegPill("Nhân Rust", selected = false, modifier = Modifier.weight(1f)) {}
                                }
                            }
                            McCard(
                                title = "Kết nối LAN & Playit",
                                trailing = { StatusPill("25565", glass.skyCyan) }
                            ) {
                                HintNote("Đồng bộ tự động cổng Java PC (TCP) và Bedrock PE (UDP 19132).")
                            }
                            McCard(
                                title = "Hiệu năng & Nhiệt độ",
                                trailing = { StatusPill("36°C", glass.liveGreen) }
                            ) {
                                ProgressTrack(percent = 44)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "RAM 2.2 / 5.0 GB • TPS 20.0 ổn định trên nhân ARM64.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            McCard(
                                title = "Sao lưu đám mây Telegram",
                                trailing = { StatusPill("Tự động", glass.accent) }
                            ) {
                                Text(
                                    "Cuộn mượt trực tiếp phía dưới viên thuốc kính đỉnh và thanh dock kính nổi đáy.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            McCard(
                                title = "Kho Mod & Plugin Modrinth",
                                trailing = { StatusPill("18 gói", glass.liveGreen) }
                            ) {
                                HintNote("Tự động kiểm tra thư viện Fabric API và dọn xung đột phiên bản 1.21.4.")
                            }
                            McCard(
                                title = "Nhật ký Console trực tiếp",
                                trailing = { StatusPill("Live", glass.skyCyan) }
                            ) {
                                Text(
                                    "[Server thread/INFO]: Done (3.42s)! Mặt kính trong suốt nhìn rõ sao đêm và dãy núi Horizon.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun fiveTabsKeepConsoleSecondAndEveryPageHasATitle() {
        assertEquals(5, mineHostMainTabs.size)
        assertEquals(5, mineHostMainTabs.map { it.first }.distinct().size)
        assertEquals(McTab.Terminal, mineHostMainTabs[1].first)
        SubPage.entries.forEach { assertTrue(mineHostPageTitle(McTab.Dashboard, it, false).isNotBlank()) }
    }

    @Test fun bottomNavigationFitsSmallPhoneAndLargeFont() {
        var selected: McTab? = null
        show(fontScale = 1.8f) {
            Column {
                Spacer(Modifier.weight(1f))
                MineHostBottomBar(McTab.Dashboard) { selected = it }
            }
        }
        mineHostMainTabs.forEach { (_, label) -> compose.onNodeWithContentDescription(label).assertIsDisplayed() }
        screenshot("bottom-320-large-font")
        assertTextFits()
        compose.onNodeWithContentDescription("Console").performClick()
        assertEquals(McTab.Terminal, selected)
    }

    @Test fun menuOpensWithoutNavigatingAndSelectionClosesIt() {
        var selected: McTab? = null
        var selectedTool: SubPage? = null
        show {
            MineHostNavigation(
                "Máy chủ",
                "Paper sinh tồn",
                McTab.Dashboard,
                null,
                onTab = { selected = it },
                onNavigate = { selectedTool = it }
            ) {
                Text("Nội dung máy chủ")
            }
        }
        compose.onNodeWithContentDescription("Mở danh mục").performClick()
        compose.onNodeWithContentDescription("Đóng danh mục").assertIsDisplayed()
        assertNull(selected)
        assertNull(selectedTool)
        screenshot("drawer-320")
        assertTextFits()
        compose.onNodeWithText("Console").performClick()
        compose.waitForIdle()
        assertEquals(McTab.Terminal, selected)
        compose.onNodeWithText("Danh mục").assertIsNotDisplayed()
    }

    @Test fun disclosurePreservesDraftAndNeverRunsActionOnExpansion() {
        var calls = 0
        show(dark = true) {
            Column(Modifier.padding(16.dp)) {
                HostDisclosure("Tọa độ", "Mở để nhập mốc và xác nhận lưu.") {
                    var draft by rememberSaveable { mutableStateOf("") }
                    OutlinedTextField(draft, { draft = it }, label = { Text("Tên mốc") })
                    Button(onClick = { calls++ }) { Text("Lưu mốc") }
                }
            }
        }
        compose.onNodeWithText("Tọa độ").performClick()
        compose.onNodeWithText("Tên mốc").performTextInput("Nhà sinh tồn")
        compose.onNodeWithText("Tọa độ").performClick()
        compose.onNodeWithText("Tọa độ").performClick()
        compose.onNodeWithText("Nhà sinh tồn").assertExists()
        assertEquals(0, calls)
        compose.onNodeWithText("Lưu mốc").performClick()
        assertEquals(1, calls)
        screenshot("disclosure-dark-320")
    }

    @Test fun longActionsStackWithoutClippingOnSmallPhone() {
        show(fontScale = 1.5f) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HostAction("world_sinh_ton_cua_nhom_ban_2026", "Đang sử dụng · Java · 128 MB", Icons.Outlined.Public) {}
                AdaptiveActionRow {
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Sao lưu thế giới hiện tại") }
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Xuất bản sao lưu ZIP") }
                }
            }
        }
        val first = compose.onNodeWithText("Sao lưu thế giới hiện tại").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Xuất bản sao lưu ZIP").fetchSemanticsNode().boundsInRoot
        assertTrue(second.top > first.bottom)
        screenshot("actions-320-large-font")
        assertTextFits()
    }

    @Test fun allPalettesKeepBodyAndButtonTextReadable() {
        assertEquals(9, EyeCareColorPreset.entries.size)
        val defaultGlass = horizonGlassTokensFor(EyeCareColorPreset.SoftSlate)
        assertEquals(15, defaultGlass.skyStops.size)
        assertTrue(defaultGlass.glassCard.alpha <= 0.30f)
        assertTrue(defaultGlass.glassChrome.alpha <= 0.38f)
        assertTrue(defaultGlass.glassRow.alpha <= 0.14f)

        EyeCareColorPreset.entries.forEach { preset ->
            val scheme = mineHostColorScheme(preset)
            listOf(
                scheme.onSurface to scheme.surface,
                scheme.onSurfaceVariant to scheme.surface,
                scheme.onSurfaceVariant to scheme.surfaceVariant,
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer
            ).forEach { (text, background) ->
                val a = text.luminance()
                val b = background.luminance()
                val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                assertTrue("$preset has insufficient text contrast: $ratio", ratio >= 5.0f)
            }
        }
    }

    @Test fun horizonGlassPresetsRenderCleanlyOnMobile() {
        var activePreset by mutableStateOf(EyeCareColorPreset.SoftSlate)
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller!!.get().setContent {
            val scheme = mineHostColorScheme(activePreset)
            val glass = horizonGlassTokensFor(activePreset)
            val scrollState = rememberScrollState(0)
            MaterialTheme(colorScheme = scheme, typography = McTypography) {
                CompositionLocalProvider(LocalHorizonGlass provides glass) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        contentColor = scheme.onSurface
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            HorizonGlassBackdrop()
                            HorizonMobileScrollablePreview(scrollState = scrollState)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Chủ đề giao diện").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mở danh mục").assertIsDisplayed()
        val viewportBounds = compose.onNodeWithTag("horizon-scroll-viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, viewportBounds.top, 0.5f)
        assertTextFits()
        screenshot("horizon-dusk-nav-320")

        compose.runOnUiThread { activePreset = EyeCareColorPreset.SakuraGlass }
        compose.waitForIdle()
        assertTextFits()
        screenshot("horizon-sakura-nav-320")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun horizonScrolledGlassProvesTrueTransparency() {
        var capturedScroll: ScrollState? = null
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller!!.get().setContent {
            val scheme = mineHostColorScheme(EyeCareColorPreset.SoftSlate)
            val glass = horizonGlassTokensFor(EyeCareColorPreset.SoftSlate)
            val initialScrollPx = with(LocalDensity.current) { 56.dp.roundToPx() }
            val scrollState = rememberScrollState(initial = initialScrollPx)
            capturedScroll = scrollState
            MaterialTheme(colorScheme = scheme, typography = McTypography) {
                CompositionLocalProvider(LocalHorizonGlass provides glass) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        contentColor = scheme.onSurface
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            HorizonGlassBackdrop()
                            HorizonMobileScrollablePreview(scrollState = scrollState)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertTrue("Expected non-zero scroll behind glass chrome", (capturedScroll?.value ?: 0) > 0)
        assertTextFits()
        screenshot("horizon-scrolled-glass-360")
    }
}
