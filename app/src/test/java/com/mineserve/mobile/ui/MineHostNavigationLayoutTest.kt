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
        compose.waitForIdle()
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
}
