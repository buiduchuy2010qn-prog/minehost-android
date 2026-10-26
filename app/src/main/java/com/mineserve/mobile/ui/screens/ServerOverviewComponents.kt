package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.ui.MinecraftArtKind
import com.mineserve.mobile.ui.MinecraftPixelArtIcon

/** Stateless presentation shared by the live overview and runner-rendered layout tests. */
@Composable
internal fun ServerOverviewCard(
    name: String,
    description: String,
    storage: String?,
    status: String,
    statusColor: Color,
    logo: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("server-overview"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                logo()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(description, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = statusColor, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(7.dp)) {}
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            if (!storage.isNullOrBlank()) Text(storage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
internal fun ServerMetricsRow(
    tps: String,
    players: String,
    memory: String,
    maxMemoryMb: Int,
    onConsole: () -> Unit,
    onPlayers: () -> Unit,
    onMemory: () -> Unit
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp)
    val labels = listOf("TPS", "Người chơi", "RAM /" + maxMemoryMb + "MB")
    val values = listOf(tps, players, memory)
    // Measure labels at the narrowest cell width before laying out the row. This
    // avoids cyclic intrinsic/fillMaxHeight passes when live values change.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidthPx = with(density) { ((maxWidth - 12.dp).toPx() / 3f).toInt() }
        val paddingPx = with(density) { 16.dp.roundToPx() }
        val contentHeightPx = values.indices.maxOf { index ->
            val value = values[index]
            val showArt = density.fontScale <= 1.3f && value.length <= 4
            val artWidthPx = with(density) { if (showArt) 20.dp.roundToPx() else 0 }
            val valueHeight = textMeasurer.measure(value,
                style = valueStyle.copy(fontSize = if (value.length > 4) 14.sp else 18.sp),
                constraints = Constraints(maxWidth = (cellWidthPx - paddingPx - artWidthPx).coerceAtLeast(1))).size.height
            val labelHeight = textMeasurer.measure(labels[index], style = labelStyle,
                constraints = Constraints(maxWidth = (cellWidthPx - paddingPx).coerceAtLeast(1))).size.height
            maxOf(valueHeight, with(density) { if (showArt) 16.dp.roundToPx() else 0 }) + labelHeight
        }
        val cellHeight = with(density) { contentHeightPx.toDp() } + 20.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ServerMetric("TPS", tps, labels[0], MinecraftArtKind.REDSTONE, Modifier.weight(1f).height(cellHeight), onConsole)
            ServerMetric("Người chơi", players, labels[1], MinecraftArtKind.DIAMOND_SWORD, Modifier.weight(1f).height(cellHeight), onPlayers)
            ServerMetric("RAM", memory, labels[2], MinecraftArtKind.COMMAND_BLOCK, Modifier.weight(1f).height(cellHeight), onMemory)
        }
    }
}

@Composable
private fun ServerMetric(label: String, value: String, detail: String, art: MinecraftArtKind, modifier: Modifier, onClick: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale > 1.3f
    Card(
        onClick = onClick,
        modifier = modifier.testTag("metric-" + label),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    fontSize = if (value.length > 4) 14.sp else 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                if (!largeFont && value.length <= 4) MinecraftPixelArtIcon(art, size = 16.dp)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ServerControlActions(
    primaryLabel: String,
    secondaryLabel: String,
    running: Boolean,
    primaryEnabled: Boolean,
    secondaryEnabled: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val primary: @Composable (Modifier) -> Unit = { modifier ->
        Button(onClick = onPrimary, enabled = primaryEnabled, modifier = modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Icon(if (running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(primaryLabel, fontWeight = FontWeight.SemiBold)
        }
    }
    val secondary: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedButton(onClick = onSecondary, enabled = secondaryEnabled, modifier = modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text(secondaryLabel)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 260.dp || largeFont) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                primary(Modifier.fillMaxWidth())
                secondary(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                primary(Modifier.weight(1.2f))
                secondary(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ServerShortcuts(
    serverCount: Int,
    onCreate: () -> Unit,
    onServers: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onCreate, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
            Text("+ Tạo server")
        }
        TextButton(onClick = onServers, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
            Text("Quản lý ("+serverCount+")")
        }
    }
}

@Composable
internal fun ServerReadySoundSetting(soundEnabled: Boolean, onSoundChange: (Boolean) -> Unit, onPreview: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Chuông khi sẵn sàng", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onPreview) { Icon(Icons.Outlined.VolumeUp, "Nghe thử chuông") }
        Switch(checked = soundEnabled, onCheckedChange = onSoundChange,
            modifier = Modifier.testTag("server-ready-sound").semantics { contentDescription = "Chuông khi sẵn sàng" })
    }
}


@Composable
internal fun ServerAddressRow(label: String, address: String?, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(address ?: "Chưa có địa chỉ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        IconButton(onClick = onCopy, enabled = address != null) { Icon(Icons.Outlined.ContentCopy, "Sao chép " + label) }
    }
}
