package com.mineserve.mobile.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.ui.components.MhBentoMetricCard
import com.mineserve.mobile.ui.components.MhCard
import com.mineserve.mobile.ui.components.MhCopyAddressRow
import com.mineserve.mobile.ui.components.MhEngineOptionCard
import com.mineserve.mobile.ui.components.MhSectionHeader
import com.mineserve.mobile.ui.components.MhStatusBadge
import com.mineserve.mobile.ui.screens.AdaptiveActionRow
import com.mineserve.mobile.ui.screens.HostAction
import com.mineserve.mobile.ui.screens.HostDisclosure
import com.mineserve.mobile.ui.theme.EyeCareColorPreset
import com.mineserve.mobile.ui.theme.LocalMineHostColors
import com.mineserve.mobile.ui.theme.LocalReducedMotion
import com.mineserve.mobile.ui.theme.McTypography
import com.mineserve.mobile.ui.theme.mineHostColorScheme
import com.mineserve.mobile.ui.theme.mineHostColors
import com.mineserve.mobile.ui.theme.mineHostSemanticColors
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
        val preset = if (dark) EyeCareColorPreset.SoftSlate else EyeCareColorPreset.WarmPaperLight
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        android.provider.Settings.Global.putFloat(
            controller!!.get().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        controller!!.get().setContent {
            MaterialTheme(colorScheme = mineHostColorScheme(preset), typography = McTypography) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                    LocalMineHostColors provides mineHostSemanticColors(preset),
                    LocalReducedMotion provides true
                ) {
                    Surface(Modifier.fillMaxSize(), content = content)
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun closeActivity() {
        controller?.pause()?.stop()?.destroy()
        controller = null
        OfficialCoreLogos.clearCache()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
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
            val semantic = mineHostSemanticColors(preset)
            listOf(
                scheme.onSurface to scheme.surface,
                scheme.onSurfaceVariant to scheme.surface,
                scheme.onSurfaceVariant to scheme.surfaceVariant,
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                semantic.onSuccess to semantic.success,
                semantic.onSuccessContainer to semantic.successContainer,
                semantic.onWarning to semantic.warning,
                semantic.onWarningContainer to semantic.warningContainer,
                semantic.onRust to semantic.rust,
                semantic.onRustContainer to semantic.rustContainer
            ).forEach { (text, background) ->
                val a = text.luminance()
                val b = background.luminance()
                val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                assertTrue("$preset has insufficient text contrast: $ratio", ratio >= 4.5f)
            }
        }
    }

    @Composable
    private fun RenderEmptyOnboarding(dark: Boolean) {
        val sem = MaterialTheme.mineHostColors
        MineHostNavigation(
            title = "Máy chủ",
            serverName = null,
            tab = McTab.Dashboard,
            page = null,
            onTab = {},
            onNavigate = {},
            liveStatusPillText = if (dark) null else "Sẵn sàng",
            liveStatusState = 0
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MhCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Chào mừng đến MineHost", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Chọn nhân máy chủ bên dưới để khởi tạo ngay.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                "Máy tầm trung · Trống 3200 MB RAM · Còn 42.5 GB",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                MhEngineOptionCard(
                    title = "Server Rust thuần",
                    badge = "0% Java",
                    subtitle = "Nhẹ ~50–90 MB RAM · Chơi PC & PE đa phiên bản",
                    accent = sem.rust,
                    accentContainer = sem.rustContainer,
                    onAccentContainer = sem.onRustContainer,
                    coreLogo = ServerCore.Pumpkin,
                    onClick = {}
                )

                MhEngineOptionCard(
                    title = "Server Java / PE",
                    badge = "Mod & Plugin",
                    subtitle = "Paper, Fabric, Forge, NeoForge, Spigot & Bedrock",
                    accent = MaterialTheme.colorScheme.primary,
                    accentContainer = MaterialTheme.colorScheme.primaryContainer,
                    onAccentContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = Icons.Rounded.Extension,
                    onClick = {}
                )
            }
        }
    }

    @Test fun v320HomeEmptyDarkFitsAndRenders() {
        show(dark = true) { RenderEmptyOnboarding(dark = true) }
        screenshot("v320-home-empty-dark")
        assertTextFits()
    }

    @Test fun v320HomeEmptyLightFitsAndRenders() {
        show(dark = false) { RenderEmptyOnboarding(dark = false) }
        screenshot("v320-home-empty-light")
        assertTextFits()
    }

    @Test fun v320HomeActiveDarkFitsAndRenders() {
        show(dark = true) {
            val sem = MaterialTheme.mineHostColors
            MineHostNavigation(
                title = "Máy chủ",
                serverName = "Pumpkin Survival",
                tab = McTab.Dashboard,
                page = null,
                onTab = {},
                onNavigate = {},
                liveStatusPillText = "20.0 TPS · 37°C",
                liveStatusState = 2
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MhCard(borderColor = sem.success.copy(alpha = 0.6f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MhStatusBadge(
                                label = "Đang chạy",
                                accent = sem.success,
                                container = sem.successContainer,
                                contentColor = sem.onSuccessContainer,
                                pulsing = false
                            )
                            Text(
                                text = "Pumpkin · 0% Java",
                                style = MaterialTheme.typography.labelSmall,
                                color = sem.rust
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f), RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CoreBrandLogo(core = ServerCore.Pumpkin, size = 36.dp)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Pumpkin Survival", style = MaterialTheme.typography.titleLarge, maxLines = 2)
                                Text(
                                    "Rust ARM64 · 1.21.11 · :25565",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = sem.rust,
                                    maxLines = 2
                                )
                            }
                        }
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = sem.rust, contentColor = sem.onRust),
                            shape = com.mineserve.mobile.ui.theme.McShape.Control,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bật Server Rust", maxLines = 1)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MhBentoMetricCard(
                            label = "TPS nhịp game",
                            value = "20.0",
                            icon = Icons.Rounded.Speed,
                            accentColor = sem.success,
                            sparklineSamples = listOf(19.6f, 19.9f, 20.0f, 19.8f, 20.0f, 20.0f),
                            sparklineMax = 20f,
                            modifier = Modifier.weight(1f)
                        )
                        MhBentoMetricCard(
                            label = "RAM (Rust)",
                            value = "54 MB",
                            icon = Icons.Rounded.Memory,
                            accentColor = sem.rust,
                            sparklineSamples = listOf(48f, 52f, 55f, 54f, 53f, 54f),
                            sparklineMax = 256f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MhBentoMetricCard(
                            label = "Người chơi",
                            value = "4",
                            icon = Icons.Rounded.Group,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        MhBentoMetricCard(
                            label = "Nhiệt độ & Pin",
                            value = "37°C · 84%",
                            icon = Icons.Rounded.Thermostat,
                            accentColor = sem.success,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    MhCard {
                        MhSectionHeader(
                            title = "Kết nối & Mời bạn",
                            subtitle = "Chơi từ xa qua Playit.gg",
                            trailing = {
                                MhStatusBadge(
                                    label = "Playit Online",
                                    accent = sem.success,
                                    container = sem.successContainer,
                                    contentColor = sem.onSuccessContainer
                                )
                            }
                        )
                        MhCopyAddressRow(
                            platformLabel = "Java / Máy tính",
                            address = "sample-host.gl.joinmc.link:25565",
                            fallbackText = "Chưa có IP",
                            icon = Icons.Rounded.Computer,
                            subtitle = "Tên miền chuẩn Playit (TCP 25565)",
                            onCopy = {}
                        )
                        MhCopyAddressRow(
                            platformLabel = "Bedrock / Điện thoại (PE)",
                            address = "147.185.221.20:19132",
                            fallbackText = "Chưa có IP",
                            icon = Icons.Rounded.Smartphone,
                            subtitle = "Cổng UDP: 19132",
                            onCopy = {}
                        )
                        AdaptiveActionRow {
                            Button(
                                onClick = {},
                                shape = com.mineserve.mobile.ui.theme.McShape.Control,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Chia sẻ thẻ mời", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {},
                                shape = com.mineserve.mobile.ui.theme.McShape.Control,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Thiết lập mạng", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        screenshot("v320-home-active-dark")
        assertTextFits()
    }
}
