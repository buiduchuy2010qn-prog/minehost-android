package com.mineserve.mobile.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mineserve.mobile.ui.screens.AdaptiveActionRow
import com.mineserve.mobile.ui.screens.HostAction
import com.mineserve.mobile.ui.screens.*
import com.mineserve.mobile.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mineserve.mobile.data.ServerCore
import org.robolectric.RuntimeEnvironment
import com.mineserve.mobile.ui.screens.HostDisclosure
import com.mineserve.mobile.ui.theme.McTypography
import com.mineserve.mobile.ui.theme.EyeCareColorPreset
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

    private fun show(fontScale: Float = 1f, dark: Boolean = false, content: @Composable () -> Unit) {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller!!.get().setContent {
            MaterialTheme(colorScheme = mineHostColorScheme(if (dark) EyeCareColorPreset.SoftSlate else EyeCareColorPreset.WarmPaperLight), typography = McTypography) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Surface(Modifier.fillMaxSize(), content = content)
                }
            }
        }
        try {
            compose.waitForIdle()
        } catch (failure: Throwable) {
            val testName = Throwable().stackTrace.firstOrNull {
                it.className == javaClass.name && it.methodName != "show" && !it.methodName.contains("$")
            }?.methodName ?: "unknown"
            screenshot("failed-" + testName)
            throw failure
        }
    }

    @After fun closeActivity() { controller?.pause()?.stop()?.destroy() }

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
                        assertFalse("Text clipped: ${result.layoutInput.text}; line=$line; bounds=${result.getLineLeft(line)}..${result.getLineRight(line)}; size=${result.size}", clipped)
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
            MineHostNavigation("Máy chủ", "Paper sinh tồn", McTab.Dashboard, null,
                onTab = { selected = it }, onNavigate = { selectedTool = it }) {
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
                assertTrue("$preset has insufficient text contrast: $ratio", ratio >= 4.5f)
            }
        }
    }

    @Composable
    private fun OverviewFixture(running: Boolean = false) {
        Column {
            Box(Modifier.weight(1f)) {
                MineHostNavigation("Máy chủ", "Fabric-1.21.11", McTab.Dashboard, null, {}, {}) {
                    HostPage("Máy chủ", "Điều khiển server, lấy IP và mời bạn bè.") {
                        item {
                            ServerOverviewCard("Fabric-1.21.11", "Fabric · 1.21.11 · Cổng 25565",
                                "Tổng 157.2 MB · Map 2.1 MB · Mod/Plugin 17.2 MB",
                                if (running) "Đang hoạt động" else "Đã dừng · Sẵn sàng khởi động",
                                MaterialTheme.colorScheme.primary,
                                logo = { IconButton(onClick = {}) { CoreBrandLogo(ServerCore.Fabric, size = 36.dp) } }) {
                                ServerMetricsRow(if (running) "20.0" else "—", if (running) "12" else "—",
                                    if (running) "768" else "—", 1024, {}, {}, {})
                                ServerControlActions(if (running) "Lưu & dừng" else "Bật server",
                                    if (running) "Khởi động lại" else "Dọn RAM", running, true, true, {}, {})
                                HorizontalDivider()
                                ServerShortcuts(1, {}, {})
                            }
                        }
                        item {
                            HostCard("IP kết nối của server", MinecraftArtKind.ENDER_EYE) {
                                ServerAddressRow("Java / máy tính", if (running) "minehost-example.tun.ply.gg:25565" else null) {}
                                ServerAddressRow("Bedrock / điện thoại", if (running) "example.ply.gg:19132" else null) {}
                                Text("Playit.gg", style = MaterialTheme.typography.titleSmall)
                                Text("Dữ liệu minh họa kiểm thử", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            MineHostBottomBar(McTab.Dashboard) {}
        }
    }

    @Test fun stoppedOverviewShowsIpBelowCompactCardAt320() {
        show(dark = true) { OverviewFixture() }
        compose.onNodeWithText("Ng??i ch?i").assertDoesNotExist()
        compose.onNodeWithText("?").assertDoesNotExist()
        compose.onAllNodesWithText("—").assertCountEquals(3)
        val metrics = listOf("TPS", "Người chơi", "RAM").map { compose.onNodeWithTag("metric-" + it).fetchSemanticsNode().boundsInRoot }
        assertEquals(metrics[0].top, metrics[1].top, 1f)
        assertEquals(metrics[1].top, metrics[2].top, 1f)
        assertEquals(metrics[0].width, metrics[2].width, 1f)
        assertTrue(metrics[0].right < metrics[1].left && metrics[1].right < metrics[2].left)
        val card = compose.onNodeWithTag("server-overview").fetchSemanticsNode().boundsInRoot
        assertTrue("Hero too tall: " + card.height, card.height < 400f)
        compose.onNodeWithText("Java / máy tính").assertIsDisplayed()
        compose.onNodeWithText("+ Tạo server").assertIsDisplayed()
        compose.onNodeWithText("Tạo mới").assertDoesNotExist()
        assertTextFits()
        screenshot("overview-stopped-dark-320")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-mdpi")
    fun runningOverviewAt360KeepsBothActionsTogether() {
        show(dark = true) { OverviewFixture(running = true) }
        val primary = compose.onNodeWithText("Lưu & dừng").fetchSemanticsNode().boundsInRoot
        val secondary = compose.onNodeWithText("Khởi động lại").fetchSemanticsNode().boundsInRoot
        assertEquals(primary.center.y, secondary.center.y, 1f)
        assertTextFits()
        screenshot("overview-running-dark-360")
    }

    @Test fun overviewAlsoRendersInLightTheme() {
        show { OverviewFixture(running = true) }
        assertTextFits()
        screenshot("overview-running-light-320")
    }

    @Test fun threeMetricsAndControlsKeepCallbacksAtLargeFont() {
        val calls = IntArray(7)
        show(fontScale = 1.8f, dark = true) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ServerMetricsRow("20.0", "123", "16384", 32768, { calls[0]++ }, { calls[1]++ }, { calls[2]++ })
                ServerControlActions("Lưu & dừng", "Khởi động lại", true, true, true, { calls[3]++ }, { calls[4]++ })
                ServerShortcuts(12, { calls[5]++ }, { calls[6]++ })
                ServerReadySoundSetting(true, {}, {})
            }
        }
        assertTrue(calls.all { it == 0 })
        val metrics = listOf("TPS", "Người chơi", "RAM").map { compose.onNodeWithTag("metric-" + it).fetchSemanticsNode().boundsInRoot }
        assertEquals(metrics[0].top, metrics[2].top, 1f)
        assertEquals(metrics[0].height, metrics[2].height, 1f)
        assertTextFits()
        screenshot("overview-controls-320-large-font")
        listOf("TPS", "Người chơi", "RAM").forEach { compose.onNodeWithTag("metric-" + it).performClick() }
        listOf("Lưu & dừng", "Khởi động lại", "+ Tạo server", "Quản lý (12)").forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        assertArrayEquals(intArrayOf(1, 1, 1, 1, 1, 1, 1), calls)
    }

    @Test fun minecraftIconsUseOriginalFramePixelsInsteadOfSquashedAtlases() {
        val resources = RuntimeEnvironment.getApplication().resources
        val raw = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.minehost_minecraft_command_block,
            android.graphics.BitmapFactory.Options().apply { inScaled = false })
        val frame = minecraftTextureBitmap(resources, MinecraftArtKind.COMMAND_BLOCK)
        assertEquals(16, frame.width)
        assertEquals(16, frame.height)
        for (y in 0 until 16) for (x in 0 until 16) assertEquals(raw.getPixel(x, y), frame.getPixel(x, y))
        val creeper = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.minehost_minecraft_creeper)
        val face = minecraftTextureBitmap(resources, MinecraftArtKind.CREEPER)
        assertEquals(8, face.width)
        for (y in 0 until 8) for (x in 0 until 8) assertEquals(creeper.getPixel(x + 8, y + 8), face.getPixel(x, y))
        MinecraftArtKind.entries.forEach { kind ->
            val bitmap = minecraftTextureBitmap(resources, kind)
            assertEquals(kind.name, bitmap.width, bitmap.height)
        }
        show(dark = true) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MinecraftArtKind.entries.forEach { kind ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MinecraftPixelArtIcon(kind, size = 32.dp)
                        Text(kind.titleVi)
                    }
                }
            }
        }
        screenshot("minecraft-original-textures")
    }

    @Test fun liveMetricsCanUpdateWithoutRelayoutLoop() {
        var running by mutableStateOf(false)
        show {
            Column(Modifier.padding(12.dp)) {
                ServerMetricsRow(if (running) "19.8" else "—", if (running) "12" else "—",
                    if (running) "1024" else "—", 2048, {}, {}, {})
            }
        }
        compose.runOnIdle { running = true }
        compose.onNodeWithText("1024").assertIsDisplayed()
        assertTextFits()
        compose.runOnIdle { running = false }
        compose.onAllNodesWithText("—").assertCountEquals(3)
    }

    @org.junit.Before fun failFastForLayoutDiagnostics() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
    }

    @Test fun isolatedRunningControlsSettle() {
        show { Column(Modifier.padding(12.dp)) {
            ServerControlActions("Lưu & dừng", "Khởi động lại", true, true, true, {}, {})
        } }
        assertTextFits()
        screenshot("isolated-running-controls")
    }

    @Test fun isolatedLargeMetricsSettle() {
        show(fontScale = 1.8f) { Column(Modifier.padding(12.dp)) {
            ServerMetricsRow("20.0", "123", "16384", 32768, {}, {}, {})
        } }
        assertTextFits()
        screenshot("isolated-large-metrics")
    }

    @Test fun isolatedSoundSettingSettles() {
        show(fontScale = 1.8f) { Column(Modifier.padding(12.dp)) {
            ServerReadySoundSetting(true, {}, {})
        } }
        assertTextFits()
    }

    @Test fun isolatedEnabledAddressSettles() {
        show { Column(Modifier.padding(12.dp)) {
            ServerAddressRow("Java / máy tính", "minehost-example.tun.ply.gg:25565") {}
        } }
        assertTextFits()
    }
}
