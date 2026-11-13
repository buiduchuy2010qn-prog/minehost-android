package com.mineserve.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.server.CrossplayCompat
import com.mineserve.mobile.server.McServerController
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.horizonGlassPlate

/**
 * Trạng thái kiểm tra chơi chung PC + PE cho một cặp core + phiên bản.
 * Trả về null khi đang kiểm tra (hoặc khi [enabled] = false).
 */
@Composable
fun rememberCrossplayCheck(
    core: ServerCore,
    mcVersion: String,
    enabled: Boolean = true,
    refreshKey: Int = 0,
    targetPe: String? = null
): CrossplayCompat.Result? {
    val pe = CrossplayCompat.normalizeTargetPe(targetPe)
    val state by produceState<CrossplayCompat.Result?>(
        if (enabled && pe == null) CrossplayCompat.cachedResult(core, mcVersion) else null,
        core, mcVersion, enabled, refreshKey, pe
    ) {
        if (!enabled || mcVersion.isBlank()) {
            value = null
            return@produceState
        }
        value = null
        value = runCatching { CrossplayCompat.evaluate(core, mcVersion, pe, force = refreshKey > 0) }.getOrNull()
            ?: if (pe == null) CrossplayCompat.cachedResult(core, mcVersion) else null
    }
    return state
}

/**
 * Chọn bản Minecraft PE đang dùng trên điện thoại:
 * - Ấn vào hộp chọn để mở bảng lướt chọn đầy đủ
 * - Hoặc nhập tay số phiên bản -> hiện kết quả ngay để ấn chọn
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeVersionPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = HorizonGlass
    val accentInk = if (glass.isLight) MaterialTheme.colorScheme.primary else glass.accent
    val greenInk = if (glass.isLight) Color(0xFF047857) else glass.liveGreen
    val coralInk = if (glass.isLight) Color(0xFFB91C1C) else glass.dangerCoral

    val current = CrossplayCompat.normalizeTargetPe(selected)
    var showPickerModal by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val latestFirst = CrossplayCompat.lastKnownInfo().bedrockVersions.firstOrNull() ?: "26.30"

    val allPeVersions = remember {
        val merged = linkedSetOf<String>()
        merged.addAll(CrossplayCompat.KNOWN_PE)
        McServerController.BEDROCK_PROTOCOL_PATCH_FAMILIES.values.forEach { family ->
            merged.addAll(family)
        }
        merged.sortedWith(Comparator { a, b ->
            val aIs26 = a.startsWith("26.") || a.startsWith("1.26.")
            val bIs26 = b.startsWith("26.") || b.startsWith("1.26.")
            if (aIs26 != bIs26) {
                if (aIs26) 1 else -1
            } else {
                CrossplayCompat.compareVersions(b, a)
            }
        })
    }

    val trimmedQuery = searchQuery.trim()
    val inlineMatches = remember(trimmedQuery, allPeVersions) {
        if (trimmedQuery.isBlank()) emptyList()
        else allPeVersions.filter { it.contains(trimmedQuery, ignoreCase = true) }.take(12)
    }
    val normalizedCustom = remember(trimmedQuery) {
        CrossplayCompat.normalizeTargetPe(trimmedQuery)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = glass.hairline
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(glass.accentSoft)
                    .border(1.dp, glass.accent.copy(alpha = 0.30f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = accentInk,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Phiên bản Minecraft PE trên điện thoại",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Chọn từ danh sách hoặc nhập số phiên bản để khớp GeyserMC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(glass.accentSoft)
                    .border(1.dp, glass.accent.copy(alpha = 0.32f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${allPeVersions.size} bản",
                    style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                    fontWeight = FontWeight.SemiBold,
                    color = accentInk
                )
            }
        }

        // Hộp bấm mở danh sách lướt chọn
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizonGlassPlate(
                    shape = RoundedCornerShape(12.dp),
                    containerColor = glass.accentSoft,
                    borderColor = glass.accent.copy(alpha = 0.45f),
                    highlighted = current != null
                )
                .clickable { showPickerModal = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (current == null) {
                        "Tự động mới nhất (PE $latestFirst+)"
                    } else {
                        "Đang chọn: Minecraft PE $current"
                    },
                    style = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                    fontWeight = FontWeight.SemiBold,
                    color = accentInk
                )
                Text(
                    text = "Chạm để mở bảng chọn phân nhánh PE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Chọn",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentInk
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = accentInk,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Ô nhập tay tìm nhanh ngay tại chỗ
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it.take(16) },
            placeholder = {
                Text(
                    text = "Nhập nhanh số bản PE (VD: 1.21.132, 1.21.51)…",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glass.glassInput,
                unfocusedContainerColor = glass.glassInput,
                focusedBorderColor = glass.accent,
                unfocusedBorderColor = glass.hairline
            ),
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("Xóa", style = MaterialTheme.typography.labelMedium, color = coralInk)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (trimmedQuery.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(
                        shape = RoundedCornerShape(12.dp),
                        containerColor = glass.glassRow,
                        borderColor = glass.hairline
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Chạm vào phiên bản để áp dụng:",
                    style = MaterialTheme.typography.labelMedium,
                    color = greenInk,
                    fontWeight = FontWeight.SemiBold
                )
                if (normalizedCustom != null && normalizedCustom !in inlineMatches) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = RoundedCornerShape(10.dp),
                                containerColor = glass.accentSoft,
                                borderColor = glass.accent.copy(alpha = 0.42f)
                            )
                            .clickable {
                                onSelect(normalizedCustom)
                                searchQuery = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Dùng bản nhập tay: PE $normalizedCustom",
                            style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                            fontWeight = FontWeight.SemiBold,
                            color = accentInk
                        )
                        Text(
                            text = "Áp dụng",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = greenInk
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(inlineMatches, key = { it }) { ver ->
                        val isSel = current == ver
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = RoundedCornerShape(10.dp),
                                    containerColor = if (isSel) glass.liveGreenSoft else glass.glassRow,
                                    borderColor = if (isSel) glass.liveGreen.copy(alpha = 0.48f) else glass.hairline
                                )
                                .clickable {
                                    onSelect(ver)
                                    searchQuery = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Minecraft PE $ver",
                                style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSel) greenInk else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isSel) "Đang chọn ✓" else "Chọn",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSel) greenInk else accentInk
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPickerModal) {
        var modalQuery by remember { mutableStateOf("") }
        var modalBranch by remember { mutableStateOf("all") }
        val modalFiltered = remember(modalQuery, modalBranch, allPeVersions) {
            val q = modalQuery.trim()
            allPeVersions.filter { v ->
                val branchOk = when (modalBranch) {
                    "1.21" -> v == "1.21" || v.startsWith("1.21.")
                    "1.20" -> v == "1.20" || v.startsWith("1.20.")
                    "1.19" -> v.startsWith("1.19")
                    "26" -> v.startsWith("26.") || v.startsWith("1.26.")
                    else -> true
                }
                branchOk && (q.isBlank() || v.contains(q, ignoreCase = true))
            }
        }
        val modalCustom = remember(modalQuery) { CrossplayCompat.normalizeTargetPe(modalQuery.trim()) }

        AlertDialog(
            onDismissRequest = { showPickerModal = false },
            modifier = Modifier.horizonGlassPlate(
                shape = McShape.Dialog,
                containerColor = glass.glassChrome,
                borderColor = glass.specularTop
            ),
            shape = McShape.Dialog,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Chọn phiên bản Minecraft PE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                ApplyHorizonDialogBlur()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = modalQuery,
                        onValueChange = { modalQuery = it.take(16) },
                        placeholder = {
                            Text(
                                text = "Tìm phiên bản PE (VD: 1.21.132, 1.21.51)…",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = glass.glassInput,
                            unfocusedContainerColor = glass.glassInput,
                            focusedBorderColor = glass.accent,
                            unfocusedBorderColor = glass.hairline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "all" to "Tất cả (${allPeVersions.size})",
                            "1.21" to "1.21.x",
                            "1.20" to "1.20.x",
                            "1.19" to "1.19.x",
                            "26" to "1.26 / 26.x"
                        ).forEach { (key, label) ->
                            SegPill(
                                text = label,
                                selected = modalBranch == key,
                                compact = true,
                                onClick = { modalBranch = key }
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(330.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (modalQuery.isBlank() && modalBranch == "all") {
                            item(key = "__auto_latest__") {
                                val isAuto = current == null
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizonGlassPlate(
                                            shape = RoundedCornerShape(11.dp),
                                            containerColor = if (isAuto) glass.liveGreenSoft else glass.accentSoft,
                                            borderColor = if (isAuto) glass.liveGreen.copy(alpha = 0.5f) else glass.accent.copy(alpha = 0.35f)
                                        )
                                        .clickable {
                                            onSelect("")
                                            showPickerModal = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Tự động mới nhất ($latestFirst+)",
                                            style = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isAuto) greenInk else accentInk
                                        )
                                        Text(
                                            text = "Tự dùng GeyserMC mới nhất cho bản PE hiện hành",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (isAuto) "Đang chọn ✓" else "Chọn",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isAuto) greenInk else accentInk
                                    )
                                }
                            }
                        }

                        if (modalCustom != null && modalCustom !in modalFiltered) {
                            item(key = "__custom_pe__") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizonGlassPlate(
                                            shape = RoundedCornerShape(11.dp),
                                            containerColor = glass.accentSoft,
                                            borderColor = glass.accent.copy(alpha = 0.45f)
                                        )
                                        .clickable {
                                            onSelect(modalCustom)
                                            showPickerModal = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Dùng bản nhập tay: PE $modalCustom",
                                        style = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentInk
                                    )
                                    Text(
                                        text = "Chọn",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = greenInk
                                    )
                                }
                            }
                        }

                        items(modalFiltered, key = { it }) { ver ->
                            val isSel = current == ver
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizonGlassPlate(
                                        shape = RoundedCornerShape(11.dp),
                                        containerColor = if (isSel) glass.liveGreenSoft else glass.glassRow,
                                        borderColor = if (isSel) glass.liveGreen.copy(alpha = 0.5f) else glass.hairline
                                    )
                                    .clickable {
                                        onSelect(ver)
                                        showPickerModal = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Minecraft PE $ver",
                                    style = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSel) greenInk else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isSel) "Đang chọn ✓" else "Chọn",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSel) greenInk else accentInk
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { showPickerModal = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, glass.hairline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = glass.glassRow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Đóng", fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}

/**
 * Thẻ hiển thị RÕ RÀNG: server Java X sẽ chơi chung được với Minecraft PE bản nào.
 * Không cho người dùng chọn "nhánh PE" giả — dải PE do bản GeyserMC thật quyết định.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrossplayCompatCard(
    core: ServerCore,
    mcVersion: String,
    result: CrossplayCompat.Result?,
    modifier: Modifier = Modifier,
    onApplySuggestion: ((ServerCore, String) -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    val glass = HorizonGlass
    val accentInk = if (glass.isLight) MaterialTheme.colorScheme.primary else glass.accent
    val greenInk = if (glass.isLight) Color(0xFF047857) else glass.liveGreen
    val coralInk = if (glass.isLight) Color(0xFFB91C1C) else glass.dangerCoral

    val ok = result?.supported == true
    val containerTint = when {
        result == null -> glass.glassRow
        ok -> glass.liveGreenSoft
        else -> glass.dangerCoralSoft
    }
    val borderTint = when {
        result == null -> glass.hairline
        ok -> glass.liveGreen.copy(alpha = 0.45f)
        else -> glass.dangerCoral.copy(alpha = 0.48f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = containerTint,
                borderColor = borderTint
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (result != null) {
                Icon(
                    imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (ok) greenInk else coralInk,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = "Tương thích Crossplay: ${core.displayName} $mcVersion" +
                    (CrossplayCompat.normalizeTargetPe(result?.targetPe)?.let { " ↔ PE $it" } ?: " ↔ Bedrock PE"),
                style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        if (result == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = glass.accent
                )
                Text(
                    text = "Đang kiểm tra dải phiên bản GeyserMC & Modrinth…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        Text(
            text = result.headline,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) greenInk else coralInk,
            lineHeight = 20.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizonGlassPlate(
                    shape = RoundedCornerShape(10.dp),
                    containerColor = glass.glassRow,
                    borderColor = glass.hairline
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = result.pcLine,
                style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
            Text(
                text = result.peLine,
                style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                fontWeight = FontWeight.SemiBold,
                color = if (ok) greenInk else coralInk,
                lineHeight = 18.sp
            )
        }

        if (ok && result.bedrockVersions.isNotEmpty()) {
            Text(
                text = "Các bản Minecraft PE vào được:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                result.bedrockVersions.forEach { v ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(glass.glassRow)
                            .border(1.dp, glass.liveGreen.copy(alpha = 0.42f), CircleShape)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "PE $v",
                            style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                            fontWeight = FontWeight.SemiBold,
                            color = greenInk
                        )
                    }
                }
            }
        }

        if (result.details.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                result.details.forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        result.suggestion?.let { s ->
            Text(
                text = "Gợi ý: $s",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = accentInk,
                lineHeight = 18.sp
            )
        }

        val sugCore = result.suggestedCore
        val sugMc = result.suggestedMcVersion
        val hasAction = (!ok && onApplySuggestion != null && sugCore != null && !sugMc.isNullOrBlank() &&
            (sugCore != core || sugMc != mcVersion)) || onRetry != null

        if (hasAction) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!ok && onApplySuggestion != null && sugCore != null && !sugMc.isNullOrBlank() &&
                    (sugCore != core || sugMc != mcVersion)
                ) {
                    Button(
                        onClick = { onApplySuggestion(sugCore, sugMc) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Đổi sang ${sugCore.displayName} $sugMc",
                            style = MaterialTheme.typography.labelMedium.merge(HorizonTabularStyle),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (onRetry != null) {
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        border = BorderStroke(1.dp, glass.hairline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = glass.glassRow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(5.dp))
                        Text(
                            text = "Kiểm tra lại",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** Tiện ích: thẻ tự kiểm tra (dùng cho các màn chỉ cần hiển thị). */
@Composable
fun CrossplayCompatAutoCard(
    core: ServerCore,
    mcVersion: String,
    modifier: Modifier = Modifier,
    onApplySuggestion: ((ServerCore, String) -> Unit)? = null
) {
    var refresh by remember { mutableStateOf(0) }
    val result = rememberCrossplayCheck(core, mcVersion, enabled = true, refreshKey = refresh)
    CrossplayCompatCard(
        core = core,
        mcVersion = mcVersion,
        result = result,
        modifier = modifier,
        onApplySuggestion = onApplySuggestion,
        onRetry = { refresh++ }
    )
}
