package com.mineserve.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.Muted

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
            .clip(RoundedCornerShape(12.dp))
            .background(Indigo.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "📱 Điện thoại bạn đang dùng Minecraft PE bản nào?",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Ấn vào khung bên dưới để lướt chọn trong ${allPeVersions.size} phiên bản PE, hoặc gõ số phiên bản vào ô tìm kiếm để ấn chọn ngay:",
            fontSize = 10.sp,
            color = Muted,
            lineHeight = 13.5.sp
        )

        // Hộp bấm mở danh sách lướt chọn
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Indigo.copy(alpha = 0.16f))
                .border(1.dp, Indigo.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .clickable { showPickerModal = true }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (current == null) "🔄 Tự động: Bản PE mới nhất ($latestFirst+)"
                    else "📱 Đang chọn: Minecraft PE $current",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Indigo
                )
                Text(
                    "Ấn vào đây để mở danh sách lướt chọn (${allPeVersions.size} bản PE)",
                    fontSize = 10.sp,
                    color = Muted
                )
            }
            Text(
                "📋 Lướt chọn ▾",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Indigo
            )
        }

        // Ô nhập tay tìm nhanh ngay tại chỗ
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it.take(16) },
            placeholder = { Text("🔍 Hoặc nhập tay bản PE (VD: 1.21.132, 1.21.51, 1.20.81)...", fontSize = 11.sp) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("Xóa", fontSize = 10.5.sp, color = Coral)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (trimmedQuery.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    "👇 Ấn vào phiên bản bên dưới để áp dụng ngay:",
                    fontSize = 10.5.sp,
                    color = Mint,
                    fontWeight = FontWeight.Bold
                )
                if (normalizedCustom != null && normalizedCustom !in inlineMatches) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Indigo.copy(alpha = 0.16f))
                            .clickable {
                                onSelect(normalizedCustom)
                                searchQuery = ""
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "✨ Dùng bản nhập tay: PE $normalizedCustom",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Indigo
                        )
                        Text(
                            "✅ Ấn để chọn",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Mint
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(inlineMatches, key = { it }) { ver ->
                        val isSel = current == ver
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Mint.copy(alpha = 0.18f) else Indigo.copy(alpha = 0.08f))
                                .clickable {
                                    onSelect(ver)
                                    searchQuery = ""
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "📱 Minecraft PE $ver",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSel) Mint else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (isSel) "✅ Đang chọn" else "👉 Ấn để chọn",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Mint else Indigo
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
            title = {
                Text(
                    "📱 Lướt chọn / Nhập tìm phiên bản PE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = modalQuery,
                        onValueChange = { modalQuery = it.take(16) },
                        placeholder = { Text("🔍 Nhập số phiên bản PE (VD: 1.21.132, 1.21.51)...", fontSize = 11.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf(
                            "all" to "Tất cả (${allPeVersions.size})",
                            "1.21" to "🌟 1.21.x",
                            "1.20" to "⚡ 1.20.x",
                            "1.19" to "🛡️ 1.19.x",
                            "26" to "🚀 1.26 / 26.x"
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
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (modalQuery.isBlank() && modalBranch == "all") {
                            item(key = "__auto_latest__") {
                                val isAuto = current == null
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isAuto) Mint.copy(alpha = 0.18f) else Indigo.copy(alpha = 0.10f))
                                        .clickable {
                                            onSelect("")
                                            showPickerModal = false
                                        }
                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "🔄 Tự động mới nhất ($latestFirst+)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isAuto) Mint else Indigo
                                        )
                                        Text(
                                            "Tự dùng GeyserMC mới nhất cho Minecraft PE hiện hành",
                                            fontSize = 10.sp,
                                            color = Muted
                                        )
                                    }
                                    Text(
                                        if (isAuto) "✅ Đang chọn" else "Chọn",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAuto) Mint else Indigo
                                    )
                                }
                            }
                        }

                        if (modalCustom != null && modalCustom !in modalFiltered) {
                            item(key = "__custom_pe__") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Indigo.copy(alpha = 0.16f))
                                        .clickable {
                                            onSelect(modalCustom)
                                            showPickerModal = false
                                        }
                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "✨ Dùng phiên bản nhập tay: PE $modalCustom",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Indigo
                                    )
                                    Text(
                                        "✅ Ấn chọn",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Mint
                                    )
                                }
                            }
                        }

                        items(modalFiltered, key = { it }) { ver ->
                            val isSel = current == ver
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (isSel) Mint.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    )
                                    .clickable {
                                        onSelect(ver)
                                        showPickerModal = false
                                    }
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "📱 Minecraft PE $ver",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSel) Mint else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (isSel) "✅ Đang chọn" else "👉 Ấn chọn",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Mint else Indigo
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickerModal = false }) {
                    Text("Đóng", color = Indigo, fontWeight = FontWeight.Bold)
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
    val ok = result?.supported == true
    val bg = when {
        result == null -> Indigo.copy(alpha = 0.10f)
        ok -> Mint.copy(alpha = 0.14f)
        else -> Coral.copy(alpha = 0.14f)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            "🔎 Kiểm tra chơi chung: ${core.displayName} Java $mcVersion ↔ Minecraft PE" +
                (CrossplayCompat.normalizeTargetPe(result?.targetPe)?.let { " $it" } ?: ""),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (result == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Indigo)
                Spacer(Modifier.size(8.dp))
                Text("Đang hỏi GeyserMC + Modrinth xem PE bản nào vào được...", fontSize = 11.sp, color = Indigo)
            }
            return@Column
        }

        Text(
            result.headline,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (ok) Mint else Coral,
            lineHeight = 17.sp
        )
        Text(result.pcLine, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp)
        Text(
            result.peLine,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) Mint else Coral,
            lineHeight = 15.sp
        )
        if (ok && result.bedrockVersions.isNotEmpty()) {
            Text("Các bản Minecraft PE vào được:", fontSize = 10.5.sp, color = Muted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                result.bedrockVersions.forEach { v ->
                    Text(
                        "📱 $v",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Mint,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Mint.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        result.details.forEach { line ->
            Text("• $line", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
        }
        result.suggestion?.let { s ->
            Text("👉 $s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Indigo, lineHeight = 15.sp)
        }
        val sugCore = result.suggestedCore
        val sugMc = result.suggestedMcVersion
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!ok && onApplySuggestion != null && sugCore != null && !sugMc.isNullOrBlank() &&
                (sugCore != core || sugMc != mcVersion)
            ) {
                Button(
                    onClick = { onApplySuggestion(sugCore, sugMc) },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Đổi sang ${sugCore.displayName} $sugMc", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("🔄 Kiểm tra lại", fontSize = 11.sp, color = Indigo) }
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
