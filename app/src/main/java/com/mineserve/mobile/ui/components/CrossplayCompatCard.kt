package com.mineserve.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.Muted

/**
 * Trạng thái kiểm tra chơi chung PC + PE cho một cặp core + phiên bản.
 * Trả về null khi đang kiểm tra (hoặc khi [enabled] = false).
 */
@Composable
fun rememberCrossplayCheck(core: ServerCore, mcVersion: String, enabled: Boolean = true, refreshKey: Int = 0): CrossplayCompat.Result? {
    val state by produceState<CrossplayCompat.Result?>(
        if (enabled) CrossplayCompat.cachedResult(core, mcVersion) else null,
        core, mcVersion, enabled, refreshKey
    ) {
        if (!enabled || mcVersion.isBlank()) {
            value = null
            return@produceState
        }
        val cached = CrossplayCompat.cachedResult(core, mcVersion)
        value = cached
        value = runCatching { CrossplayCompat.evaluate(core, mcVersion, force = refreshKey > 0) }.getOrNull() ?: cached
    }
    return state
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
            "🔎 Kiểm tra chơi chung: ${core.displayName} Java $mcVersion ↔ Minecraft PE",
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
